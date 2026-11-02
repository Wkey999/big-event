import unittest
from unittest.mock import patch

from eval.evaluate_retrieval import EVAL_SPLITTER, EvalCase, evaluate_strategy
from app.main import splitter


class EvaluationTest(unittest.TestCase):
    def test_low_positive_match_is_not_counted_as_online_rejection(self):
        case = EvalCase("unanswerable", "无答案陷阱拒答", "不存在的答案？", ())
        with patch("eval.evaluate_retrieval.EVAL_CASES", [
            EvalCase("answerable", "事实", "存在的答案？", ("答案",)), case,
        ]):
            with patch("eval.evaluate_retrieval.rank_chunks", return_value=[(0.01, 1, "答案")]):
                result = evaluate_strategy(["答案"], "vector")
        self.assertEqual(0, result["unans_reject_rate"])
        self.assertEqual(1, len(result["failures"]))

    def test_evaluation_uses_service_splitter(self):
        self.assertIs(splitter, EVAL_SPLITTER)


if __name__ == "__main__":
    unittest.main()
