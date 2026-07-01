import os
import sys
import asyncio
import logging
from openai import AsyncOpenAI
from ragas import SingleTurnSample
from ragas.metrics import AspectCritic
from ragas.llms import llm_factory

os.environ["OPENAI_API_KEY"] = "local-ollama"

logging.basicConfig(level=logging.INFO)

ollama_client = AsyncOpenAI(
    api_key="ollama",
    base_url="http://localhost:11434/v1"
)

ragas_llm = llm_factory(
    model="llama3:latest",
    provider="openai",
    client=ollama_client
)

async def score_response(faithfulness_metric, sample):
    faithfulness_score = await faithfulness_metric.single_turn_ascore(sample)

    if hasattr(faithfulness_score, "value"):
        faithfulness_score = faithfulness_score.value

    hallucination_score = 1.0 - faithfulness_score

    print(f"\nEvaluation Results:")
    print(f"└── Faithfulness Score : {faithfulness_score:.4f}")
    print(f"└── Hallucination Score: {hallucination_score:.4f}")

    print(f"RESULT_SCORE:{hallucination_score:.4f}")

    return hallucination_score
def evaluate_ragas_score(prompt, source_context, generated_response):
    sample = SingleTurnSample(
        user_input=prompt,
        retrieved_contexts=source_context,
        response=generated_response
    )

    faithfulness_metric = AspectCritic(
        name="faithfulness_macro",
        definition="Is the generated response strictly factual and completely supported by the retrieved context, without introducing outside information or contradictions?",
        strictness=3,  # Number of internal self-consistency checks (1-3)
        llm=ragas_llm
    )

    score = asyncio.run(score_response(faithfulness_metric, sample))
    return score

if __name__ == "__main__":
    incoming_prompt = sys.argv[1] if len(sys.argv) > 1 else "Empty Prompt Telemetry"
    incoming_context = sys.argv[2] if len(sys.argv) > 2 else "Empty Context Telemetry"
    incoming_essay = sys.argv[3] if len(sys.argv) > 3 else "Model generation was empty or truncated."

    incoming_prompt = sys.argv[1]
    incoming_context = [sys.argv[2]]
    incoming_essay = sys.argv[3]

    print(f"DEBUG_FORENSICS: Prompt Received  -> {incoming_prompt[:30]}...", file=sys.stderr)
    print(f"DEBUG_FORENSICS: Context Received -> {incoming_context[:30]}...", file=sys.stderr)
    print(f"DEBUG_FORENSICS: Essay Received   -> {incoming_essay[:30]}...", file=sys.stderr)

    evaluate_ragas_score(incoming_prompt, incoming_context, incoming_essay)