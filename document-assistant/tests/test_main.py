import io
import unittest
from fastapi.testclient import TestClient
from pypdf import PdfWriter
from app.main import analyze, app


class AnalyzeTest(unittest.TestCase):
    def test_finds_source_passage_for_question(self):
        result = analyze("活动报名截止日期是 11 月 5 日。参加者需提交表格。", "报名截止日期是什么时候？")
        self.assertEqual("extractive", result.mode)
        self.assertIn("11 月 5 日", result.citations[0].excerpt)
        self.assertGreater(result.citations[0].score, 0)

    def test_unknown_question_has_no_fabricated_answer(self):
        result = analyze("活动报名截止日期是 11 月 5 日。", "会议地点在哪里？")
        self.assertEqual([], result.citations)
        self.assertIn("未找到", result.answer)

    def test_summary_does_not_call_model(self):
        result = analyze("第一项说明。第二项说明。", "")
        self.assertIn("第一项说明", result.summary)
        self.assertIsNone(result.answer)

    def test_http_contract_and_input_limit(self):
        client = TestClient(app)
        response = client.post("/v1/analyze", json={"text": "报名截止日期为11月5日。", "question": "报名截止日期？"})
        self.assertEqual(200, response.status_code)
        self.assertEqual("extractive", response.json()["mode"])
        self.assertTrue(response.json()["citations"])
        self.assertEqual(422, client.post("/v1/analyze", json={"text": "x" * 20_001}).status_code)

    def test_bm25_and_hybrid_prioritize_rare_key_terms_and_track_pdf_pages(self):
        doc = (
            "[第 1 页]\n本项目技术方案说明：普通模块采用常规接口规范，项目方案适用于日常办公。\n\n"
            + ("常规说明段落。" * 95)
            + "\n\n[第 2 页]\n本项目技术方案说明：核心鉴权模块采用 SHA-256 摘要写入 Redis，TTL 与令牌剩余有效期对齐。"
        )
        bm25_res = analyze(doc, "项目方案里 Redis 鉴权摘要算法是什么？", strategy="bm25", top_k=2)
        self.assertTrue(bm25_res.citations)
        self.assertIn("SHA-256", bm25_res.citations[0].excerpt)
        self.assertEqual(2, bm25_res.citations[0].page)
        self.assertNotIn("[第 2 页]", bm25_res.citations[0].excerpt)

        hybrid_res = analyze(doc, "项目方案里 Redis 鉴权摘要算法是什么？", strategy="hybrid", top_k=2)
        self.assertIn("SHA-256", hybrid_res.citations[0].excerpt)

    def test_extract_endpoint_supports_txt_and_rejects_blank_scanned_pdf(self):
        client = TestClient(app)
        txt_resp = client.post(
            "/v1/extract",
            files={"file": ("notice.txt", "关于秋季黑客松报名的通知：截止时间为周五下午五点。".encode("utf-8"), "text/plain")},
        )
        self.assertEqual(200, txt_resp.status_code)
        self.assertEqual("txt", txt_resp.json()["file_type"])
        self.assertIn("秋季黑客松", txt_resp.json()["text"])

        bad_ext = client.post(
            "/v1/extract",
            files={"file": ("archive.zip", b"PK\x03\x04", "application/zip")},
        )
        self.assertEqual(422, bad_ext.status_code)

        # Blank PDF without text layer simulates a scanned image-only PDF
        writer = PdfWriter()
        writer.add_blank_page(width=200, height=200)
        buf = io.BytesIO()
        writer.write(buf)
        pdf_resp = client.post(
            "/v1/extract",
            files={"file": ("scan.pdf", buf.getvalue(), "application/pdf")},
        )
        self.assertEqual(422, pdf_resp.status_code)
        self.assertIn("扫描件", pdf_resp.json()["detail"])


if __name__ == "__main__":
    unittest.main()
