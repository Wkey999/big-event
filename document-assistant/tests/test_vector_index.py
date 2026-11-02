import concurrent.futures
from dataclasses import replace
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

import httpx
import numpy as np
from fastapi.testclient import TestClient

from app.main import app, clear_vector_index_cache, rank_chunks_with_meta
from app.vector_index import (
    EmbeddingConfig, EmbeddingUnavailable, UNAVAILABLE, VectorIndexStore, remote_embeddings,
)


class VectorIndexTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        env = patch.dict(os.environ, {"EMBEDDING_API_KEY": "", "VECTOR_CACHE_DIR": self.directory.name})
        env.start()
        self.addCleanup(env.stop)
        self.config = EmbeddingConfig("test-encoder-v1", local_dimension=4)
        self.chunks = ["测试片段一", "测试片段二"]
        self.matrix = np.asarray([[1, 0, 0, 0], [0, 1, 0, 0]], dtype=np.float32)
        self.encoder = Mock(return_value=self.matrix)
        self.store = VectorIndexStore(capacity=2)

    def build(self, config=None, chunks=None, store=None):
        return (store or self.store).get_or_build(
            chunks or self.chunks, {}, config or self.config, self.encoder)

    def remote_config(self):
        return replace(self.config, base_url="https://embedding.example/v1",
                       model="model-one", api_key="test-not-a-real-credential")

    def test_same_configuration_reuses_index_but_changed_document_does_not(self):
        first, hit1 = self.build()
        second, hit2 = self.build()
        self.assertFalse(hit1)
        self.assertTrue(hit2)
        self.assertIs(first, second)
        self.build(chunks=["changed document", "another chunk"])
        self.assertEqual(2, self.encoder.call_count)

    def test_models_endpoints_credentials_and_encoder_versions_have_separate_indexes(self):
        original = self.remote_config()
        variants = [original, replace(original, model="model-two"),
                    replace(original, base_url="https://other.example/v1"),
                    replace(original, api_key="different-test-credential"),
                    replace(original, encoder_revision="v2")]
        with patch("app.vector_index.remote_embeddings", return_value=self.matrix) as embed:
            for config in variants:
                _, hit = self.build(config)
                self.assertFalse(hit)
            self.assertEqual(5, embed.call_count)
        self.encoder.assert_not_called()
        self.assertEqual(5, len(list(Path(self.directory.name).glob("*.boomidx"))))
        for file in Path(self.directory.name).glob("*.boomidx"):
            self.assertNotIn(original.api_key.encode(), file.read_bytes())
            self.assertNotIn(self.chunks[0].encode(), file.read_bytes())

    def test_document_embedding_failure_never_creates_local_fallback_index(self):
        with patch("app.vector_index.remote_embeddings", side_effect=EmbeddingUnavailable(UNAVAILABLE)):
            with self.assertRaises(EmbeddingUnavailable):
                self.build(self.remote_config())
        self.encoder.assert_not_called()
        self.assertEqual([], list(Path(self.directory.name).glob("*.boomidx")))

    def test_query_failure_and_dimension_change_never_mix_vector_spaces(self):
        config = self.remote_config()
        with patch("app.vector_index.remote_embeddings", return_value=self.matrix):
            entry, _ = self.build(config)
        for result in [EmbeddingUnavailable(UNAVAILABLE), np.ones((1, 3), dtype=np.float32)]:
            with self.subTest(result=type(result).__name__):
                with patch("app.vector_index.remote_embeddings") as embed:
                    if isinstance(result, Exception):
                        embed.side_effect = result
                    else:
                        embed.return_value = result
                    with self.assertRaises(EmbeddingUnavailable):
                        self.store.search(entry, "question", self.encoder)
        self.encoder.assert_not_called()

    def test_restart_restores_vectors_without_calling_encoder(self):
        self.build()
        script = """
from app.vector_index import EmbeddingConfig, VectorIndexStore
def fail(*args):
    raise AssertionError('encoder must not run after restart')
entry, hit = VectorIndexStore().get_or_build(
    ['测试片段一', '测试片段二'], {}, EmbeddingConfig('test-encoder-v1', local_dimension=4), fail)
assert hit and entry.index.ntotal == 2 and entry.dim == 4
assert entry.index.reconstruct(0).tolist() == [1, 0, 0, 0]
print('restored')
"""
        result = subprocess.run([sys.executable, "-c", script], capture_output=True, text=True,
                                encoding="utf-8", env={**os.environ, "PYTHONIOENCODING": "utf-8"}, timeout=20)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("restored", result.stdout.strip())

    def test_corrupted_or_partial_cache_is_rebuilt(self):
        self.build()
        file = next(Path(self.directory.name).glob("*.boomidx"))
        content = file.read_bytes()
        for broken in [content[:20], content[:-1] + bytes([content[-1] ^ 1])]:
            with self.subTest(length=len(broken)):
                file.write_bytes(broken)
                self.store.clear()
                _, hit = self.build()
                self.assertFalse(hit)
                self.assertEqual([], list(Path(self.directory.name).glob(".writing-*")))
        self.assertEqual(3, self.encoder.call_count)

    def test_disk_write_failure_does_not_break_retrieval(self):
        with patch("app.vector_index.tempfile.NamedTemporaryFile", side_effect=PermissionError):
            entry, hit = self.build()
        self.assertFalse(hit)
        self.assertEqual(2, entry.index.ntotal)

    def test_disk_retention_and_memory_lru_are_bounded(self):
        with patch("app.vector_index.MAX_DISK_INDEXES", 2):
            for i in range(3):
                self.build(chunks=[f"document {i}", "part two"])
        self.assertEqual(2, len(list(Path(self.directory.name).glob("*.boomidx"))))
        self.assertEqual(2, len(self.store._cache))

    def test_concurrent_builds_of_same_document_only_encode_once(self):
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            results = list(executor.map(lambda _: self.build(), range(4)))
        self.assertEqual(1, self.encoder.call_count)
        self.assertEqual(1, sum(not hit for _, hit in results))

    def test_remote_response_is_ordered_normalized_and_validated(self):
        cases = [
            (200, {"data": [{"index": 1, "embedding": [0, 3]}, {"index": 0, "embedding": [2, 0]}]}, True),
            (200, {"data": [{"index": 0, "embedding": [1, 0]}, {"index": 0, "embedding": [0, 1]}]}, False),
            (200, {"data": [{"index": 0, "embedding": [1, 0]}]}, False),
            (200, {"data": [{"index": 0, "embedding": [0, 0]}, {"index": 1, "embedding": [0, 1]}]}, False),
            (200, {"data": [{"index": 0, "embedding": [float("nan"), 0]}, {"index": 1, "embedding": [0, 1]}]}, False),
            (200, {"data": [{"index": 0, "embedding": [1]}, {"index": 1, "embedding": [0, 1]}]}, False),
            (429, {"error": "private provider response"}, False),
        ]
        for status, body, valid in cases:
            with self.subTest(status=status, valid=valid):
                response = httpx.Response(status, content=json.dumps(body).encode(),
                                          request=httpx.Request("POST", "https://embedding.example/v1/embeddings"))
                with patch("app.vector_index.httpx.Client") as client:
                    client.return_value.__enter__.return_value.post.return_value = response
                    if valid:
                        np.testing.assert_allclose(remote_embeddings(self.chunks, self.remote_config()), [[1, 0], [0, 1]])
                    else:
                        with self.assertRaisesRegex(EmbeddingUnavailable, "BM25") as error:
                            remote_embeddings(self.chunks, self.remote_config())
                        self.assertNotIn("private", str(error.exception))
                        self.assertNotIn(self.remote_config().api_key, str(error.exception))

    def test_vector_outage_returns_503_but_lexical_retrieval_still_works(self):
        clear_vector_index_cache()
        with patch.dict(os.environ, {"EMBEDDING_API_KEY": "fake-for-test"}):
            with patch("app.vector_index.remote_embeddings", side_effect=EmbeddingUnavailable(UNAVAILABLE)) as embed:
                client = TestClient(app)
                for strategy in ("vector", "hybrid"):
                    response = client.post("/v1/analyze", json={"text": "报名截止日期是11月5日。",
                                                               "question": "报名截止日期？", "strategy": strategy})
                    self.assertEqual(503, response.status_code)
                    self.assertEqual(UNAVAILABLE, response.json()["detail"])
                self.assertEqual(2, embed.call_count)
                response = client.post("/v1/analyze", json={"text": "报名截止日期是11月5日。",
                                                           "question": "报名截止日期？", "strategy": "bm25"})
                self.assertEqual(200, response.status_code)
                self.assertEqual(2, embed.call_count)

    def test_network_timeout_does_not_expose_provider_details(self):
        with patch("app.vector_index.httpx.Client") as client:
            client.return_value.__enter__.return_value.post.side_effect = httpx.ReadTimeout("private endpoint information")
            with self.assertRaises(EmbeddingUnavailable) as error:
                remote_embeddings(self.chunks, self.remote_config())
        self.assertEqual(UNAVAILABLE, str(error.exception))

    def test_remote_semantic_results_do_not_require_handwritten_synonym_matches(self):
        chunks = ["The aurora pavilion welcomes visitors at dawn.", "Some other sentence.", "A third passage."]
        for strategy in ("vector", "hybrid"):
            with self.subTest(strategy=strategy), patch.dict(os.environ, {"EMBEDDING_MIN_SCORE": "0.5"}):
                with patch("app.main.search_faiss_index_with_mode", return_value=([0.9, 0.1, 0.1], False, True)):
                    ranked, _ = rank_chunks_with_meta(chunks, "想看极光应该何时进馆？", strategy)
                    self.assertGreater(ranked[0][0], 0)
                    self.assertEqual(1, ranked[0][1])
                    self.assertEqual(0, ranked[1][0])

    def test_remote_score_threshold_rejects_invalid_configuration(self):
        with patch.dict(os.environ, {"EMBEDDING_MIN_SCORE": "nan"}):
            with patch("app.main.search_faiss_index_with_mode", return_value=([0.9], False, True)):
                with self.assertRaises(EmbeddingUnavailable):
                    rank_chunks_with_meta(["片段"], "查找片段", "vector")

    def test_cloud_batches_are_limited_to_ten_and_reassembled_in_input_order(self):
        texts = [f"row-{i}" for i in range(23)]
        def response_for_batch(*args, **kwargs):
            batch = kwargs["json"]["input"]
            self.assertLessEqual(len(batch), 10)
            data = [{"index": i, "embedding": [int(text.split("-")[1]) + 1, 1]}
                    for i, text in enumerate(batch)]
            return httpx.Response(200, json={"data": list(reversed(data))},
                                  request=httpx.Request("POST", "https://embedding.example/v1/embeddings"))
        with patch("app.vector_index.httpx.Client") as client:
            post = client.return_value.__enter__.return_value.post
            post.side_effect = response_for_batch
            matrix = remote_embeddings(texts, self.remote_config())
        self.assertEqual([10, 10, 3], [len(call.kwargs["json"]["input"]) for call in post.call_args_list])
        self.assertEqual((23, 2), matrix.shape)
        expected = np.asarray([[i + 1, 1] for i in range(23)], dtype=np.float32)
        expected /= np.linalg.norm(expected, axis=1, keepdims=True)
        np.testing.assert_allclose(expected, matrix, atol=1e-6)

    def test_failed_second_batch_does_not_save_an_incomplete_index(self):
        response = httpx.Response(200, json={"data": [
            {"index": i, "embedding": [1, 0]} for i in range(10)]},
            request=httpx.Request("POST", "https://embedding.example/v1/embeddings"))
        with patch("app.vector_index.httpx.Client") as client:
            client.return_value.__enter__.return_value.post.side_effect = [response, httpx.ReadTimeout("failure")]
            with self.assertRaises(EmbeddingUnavailable):
                self.build(self.remote_config(), chunks=[f"document {i}" for i in range(11)])
        self.encoder.assert_not_called()
        self.assertEqual(0, len(self.store._cache))
        self.assertEqual([], list(Path(self.directory.name).glob("*.boomidx")))

    def test_dimensions_must_match_across_batches(self):
        response1 = httpx.Response(200, json={"data": [
            {"index": i, "embedding": [1, 0]} for i in range(10)]},
            request=httpx.Request("POST", "https://embedding.example/v1/embeddings"))
        response2 = httpx.Response(200, json={"data": [{"index": 0, "embedding": [1, 0, 0]}]},
                                  request=httpx.Request("POST", "https://embedding.example/v1/embeddings"))
        with patch("app.vector_index.httpx.Client") as client:
            client.return_value.__enter__.return_value.post.side_effect = [response1, response2]
            with self.assertRaises(EmbeddingUnavailable):
                remote_embeddings([str(i) for i in range(11)], self.remote_config())

    def test_exhausted_budget_stops_before_another_batch(self):
        response = httpx.Response(200, json={"data": [
            {"index": i, "embedding": [1, 0]} for i in range(10)]},
            request=httpx.Request("POST", "https://embedding.example/v1/embeddings"))
        with patch("app.vector_index.httpx.Client") as client:
            post = client.return_value.__enter__.return_value.post
            post.return_value = response
            with patch("app.vector_index.time.monotonic", side_effect=[0, 0, 1, 5]):
                with self.assertRaises(EmbeddingUnavailable):
                    remote_embeddings([str(i) for i in range(11)], self.remote_config())
            self.assertEqual(1, post.call_count)


if __name__ == "__main__":
    unittest.main()
