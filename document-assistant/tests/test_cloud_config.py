import contextlib
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from app.cloud_config import CloudConfigError, cloud_environment
import run_cloud


class CloudConfigTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.path = Path(self.directory.name) / ".env.local"

    def test_empty_key_is_rejected_with_actionable_message(self):
        self.path.write_text("EMBEDDING_API_KEY=\n", encoding="utf-8")
        with self.assertRaisesRegex(CloudConfigError, "EMBEDDING_API_KEY"):
            cloud_environment(self.path, {})

    def test_private_file_is_read_without_mutating_parent_environment(self):
        self.path.write_text('\ufeff# settings\nEMBEDDING_API_KEY="fake-local-key"\nVECTOR_CACHE_DIR=.cache\n', encoding="utf-8")
        parent = {"UNRELATED": "unchanged"}
        env = cloud_environment(self.path, parent)
        self.assertEqual("fake-local-key", env["EMBEDDING_API_KEY"])
        self.assertEqual("text-embedding-v4", env["EMBEDDING_MODEL"])
        self.assertEqual(str(Path(self.directory.name) / ".cache"), env["VECTOR_CACHE_DIR"])
        self.assertEqual({"UNRELATED": "unchanged"}, parent)

    def test_explicit_nonempty_process_secret_overrides_file(self):
        self.path.write_text("EMBEDDING_API_KEY=fake-file-key\n", encoding="utf-8")
        env = cloud_environment(self.path, {"EMBEDDING_API_KEY": "fake-process-key"})
        self.assertEqual("fake-process-key", env["EMBEDDING_API_KEY"])

    def test_bad_configuration_never_echoes_secrets(self):
        invalid_lines = ["EMBEDDING_BATCH_SIZE=11", "EMBEDDING_MIN_SCORE=nan",
                         "EMBEDDING_BASE_URL=http://example.com", "PATH=unexpected",
                         "EMBEDDING_API_KEY=duplicate", 'EMBEDDING_MODEL="unclosed']
        for invalid in invalid_lines:
            with self.subTest(invalid=invalid):
                self.path.write_text("EMBEDDING_API_KEY=fake-private-value\n" + invalid, encoding="utf-8")
                with self.assertRaises(CloudConfigError) as error:
                    cloud_environment(self.path, {})
                self.assertNotIn("fake-private-value", str(error.exception))

    def test_check_command_does_not_start_server_or_call_api(self):
        self.path.write_text("EMBEDDING_API_KEY=fake-test-key\n", encoding="utf-8")
        with patch.object(run_cloud, "ROOT", self.path.parent), patch.dict(os.environ, {}, clear=True):
            with patch("sys.argv", ["run_cloud.py", "--check"]), patch("run_cloud.subprocess.run") as start:
                with patch("app.vector_index.httpx.Client") as network, contextlib.redirect_stdout(io.StringIO()) as output:
                    self.assertEqual(0, run_cloud.main())
                start.assert_not_called()
                network.assert_not_called()
        self.assertNotIn("fake-test-key", output.getvalue())


if __name__ == "__main__":
    unittest.main()
