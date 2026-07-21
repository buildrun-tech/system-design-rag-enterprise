#!/usr/bin/env python3
"""Parseia jacoco.xml em memoria e imprime um relatorio de texto PASS/FAIL/STUCK.

Nao cria, grava ou persiste nenhum arquivo proprio no disco.
"""

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gate_signature import decide_status  # noqa: E402

FIX_INSTRUCTION = (
    "Adicione ou ajuste um teste unitario JUnit 5 + Mockito que exercite esta linha."
)


def find_gaps(jacoco_xml_path: str) -> list[str]:
    root = ET.parse(jacoco_xml_path).getroot()
    gaps = []
    for package in root.findall("package"):
        pkg_name = package.get("name", "").replace("/", ".")
        for sourcefile in package.findall("sourcefile"):
            file_name = sourcefile.get("name")
            location = f"{pkg_name}.{file_name}" if pkg_name else file_name
            for line in sourcefile.findall("line"):
                ci = int(line.get("ci", "0"))
                mi = int(line.get("mi", "0"))
                if ci == 0 and mi > 0:
                    gaps.append(f"{location}:{line.get('nr')}")
    return sorted(gaps)


def total_line_coverage(jacoco_xml_path: str) -> float:
    root = ET.parse(jacoco_xml_path).getroot()
    for counter in root.findall("counter"):
        if counter.get("type") == "LINE":
            missed = int(counter.get("missed"))
            covered = int(counter.get("covered"))
            total = missed + covered
            return (covered / total * 100.0) if total else 100.0
    return 100.0


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jacoco-xml", required=True)
    parser.add_argument("--previous-signature", default=None)
    parser.add_argument("--attempt", type=int, default=1)
    args = parser.parse_args()

    gaps = find_gaps(args.jacoco_xml)
    coverage_pct = total_line_coverage(args.jacoco_xml)
    status, signature, next_attempt = decide_status(
        gaps, args.previous_signature, args.attempt
    )

    print(f"STATUS: {status}")
    print(f"LINE_COVERAGE: {coverage_pct:.2f}%")
    print(f"SIGNATURE: {signature}")
    print(f"ATTEMPT: {next_attempt}")

    if status == "PASS":
        print("Line coverage 100%. Nenhuma acao necessaria.")
    elif status == "FAIL":
        print("Gaps de cobertura encontrados:")
        for gap in gaps:
            print(f"  - {gap}: {FIX_INSTRUCTION}")
        print(
            "Corrija os gaps acima e rode este script de novo passando "
            f"--previous-signature {signature} --attempt {next_attempt}."
        )
    else:
        print(
            f"CENARIO TRAVADO: a mesma lista de gaps se repetiu por "
            f"{next_attempt} tentativas consecutivas."
        )
        print("Gaps ainda nao cobertos:")
        for gap in gaps:
            print(f"  - {gap}")
        print(
            "Pare o loop e reporte este cenario ao usuario em vez de tentar "
            "novamente."
        )

    sys.exit({"PASS": 0, "FAIL": 1, "STUCK": 2}[status])


if __name__ == "__main__":
    main()
