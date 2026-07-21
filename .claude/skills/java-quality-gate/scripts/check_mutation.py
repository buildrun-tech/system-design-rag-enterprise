#!/usr/bin/env python3
"""Parseia mutations.xml do PITest em memoria e imprime PASS/FAIL/STUCK.

Nao cria, grava ou persiste nenhum arquivo proprio no disco.
"""

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gate_signature import decide_status  # noqa: E402

NOT_KILLED_STATUSES = {"SURVIVED", "NO_COVERAGE"}

FIX_INSTRUCTION = (
    "Escreva um teste que diferencie o comportamento original do mutante "
    "nesta linha (ou adicione cobertura, se o motivo for NO_COVERAGE)."
)


def _text(mutation: ET.Element, tag: str) -> str:
    node = mutation.find(tag)
    return node.text.strip() if node is not None and node.text else ""


def find_gaps(mutations_xml_path: str) -> list[str]:
    root = ET.parse(mutations_xml_path).getroot()
    gaps = []
    for mutation in root.findall("mutation"):
        status = mutation.get("status") or _text(mutation, "status")
        if status in NOT_KILLED_STATUSES:
            klass = _text(mutation, "mutatedClass")
            line = _text(mutation, "lineNumber")
            mutator = _text(mutation, "mutator").rsplit(".", 1)[-1]
            gaps.append(f"{klass}:{line}:{mutator} ({status})")
    return sorted(gaps)


def mutation_score(mutations_xml_path: str) -> float:
    root = ET.parse(mutations_xml_path).getroot()
    mutations = root.findall("mutation")
    total = len(mutations)
    if total == 0:
        return 100.0
    not_killed = sum(
        1
        for m in mutations
        if (m.get("status") or _text(m, "status")) in NOT_KILLED_STATUSES
    )
    return (total - not_killed) / total * 100.0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mutations-xml", required=True)
    parser.add_argument("--previous-signature", default=None)
    parser.add_argument("--attempt", type=int, default=1)
    args = parser.parse_args()

    gaps = find_gaps(args.mutations_xml)
    score = mutation_score(args.mutations_xml)
    status, signature, next_attempt = decide_status(
        gaps, args.previous_signature, args.attempt
    )

    print(f"STATUS: {status}")
    print(f"MUTATION_SCORE: {score:.2f}%")
    print(f"SIGNATURE: {signature}")
    print(f"ATTEMPT: {next_attempt}")

    if status == "PASS":
        print("Mutation score 100%. Nenhuma acao necessaria.")
    elif status == "FAIL":
        print("Mutantes nao mortos encontrados:")
        for gap in gaps:
            print(f"  - {gap}: {FIX_INSTRUCTION}")
        print(
            "Corrija os mutantes acima e rode este script de novo passando "
            f"--previous-signature {signature} --attempt {next_attempt}."
        )
    else:
        print(
            f"CENARIO TRAVADO: a mesma lista de mutantes sobreviventes se "
            f"repetiu por {next_attempt} tentativas consecutivas."
        )
        print("Mutantes ainda nao mortos:")
        for gap in gaps:
            print(f"  - {gap}")
        print(
            "Pare o loop e reporte este cenario ao usuario. So adicione "
            "excludedMethods/excludedClasses no pom.xml para estes mutantes "
            "especificos, com comentario justificando o motivo."
        )

    sys.exit({"PASS": 0, "FAIL": 1, "STUCK": 2}[status])


if __name__ == "__main__":
    main()
