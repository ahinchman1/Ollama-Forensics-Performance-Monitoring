import os
import sys
import asyncio
import json
from openai import AsyncOpenAI
from ragas import SingleTurnSample
from ragas.metrics.collections import Faithfulness
from ragas.llms import llm_factory

os.environ["OPENAI_API_KEY"] = "local-ollama"

ollama_client = AsyncOpenAI(
    api_key="ollama",  # Placeholder token required by the client initialization
    base_url="http://localhost:11434/v1"
)

ragas_llm = llm_factory(
    model="llama3:latest",
    provider="openai",
    client=ollama_client
)

async def score_response(faithfulness_metric, sample):
    contexts = sample.retrieved_contexts
    if isinstance(contexts, str):
        contexts = [contexts]

    faithfulness_score = await faithfulness_metric.ascore(
        user_input=sample.user_input,
        response=sample.response,
        retrieved_contexts=contexts
    )

    if hasattr(faithfulness_score, "value"):
        faithfulness_score = faithfulness_score.value

    hallucination_score = 1.0 - faithfulness_score

    evaluation_report = {
        "faithfulness_score": round(float(faithfulness_score), 4),
        "hallucination_score": round(float(hallucination_score), 4),
    }

    print(f"\nEvaluation Results:")
    print(f"└── Faithfulness Score : r8qtc{faithfulness_score:.4f}")
    print(f"└── Hallucination Score: {hallucination_score:.4f}")

    print(f"RESULT_SCORE:{hallucination_score:.4f}")

    return hallucination_score
def evaluate_ragas_score(prompt, source_context, generated_response):
    sample = SingleTurnSample(
        user_input=prompt,
        retrieved_contexts=source_context,
        response=generated_response
    )

    faithfulness_metric = Faithfulness(llm=ragas_llm)

    score = asyncio.run(score_response(faithfulness_metric, sample))
    return score

if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Error: Script requires 3 arguments: prompt, context, response.", file=sys.stderr)
        sys.exit(1)

    incoming_prompt = sys.argv[1]
    incoming_context = [sys.argv[2]]
    incoming_essay = sys.argv[3]

    evaluate_ragas_score(incoming_prompt, incoming_context, incoming_essay)