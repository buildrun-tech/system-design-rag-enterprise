#!/usr/bin/env python3
"""Assinatura estavel de um conjunto de gaps + decisao PASS/FAIL/STUCK.

Sem leitura ou escrita de arquivo proprio: a memoria entre chamadas viaja
como argumento de linha de comando (--previous-signature / --attempt),
nunca em disco.
"""

import hashlib

STUCK_THRESHOLD = 5


def compute_signature(gaps: list[str]) -> str:
    normalized = "\n".join(sorted(gaps))
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:16]


def decide_status(
    gaps: list[str], previous_signature: str | None, attempt: int
) -> tuple[str, str, int]:
    """Retorna (status, signature, next_attempt).

    status é PASS quando não há gaps; caso contrário FAIL (progresso ou
    primeira tentativa) ou STUCK (mesma assinatura por >= STUCK_THRESHOLD
    tentativas consecutivas).
    """
    if not gaps:
        return "PASS", "", 0

    signature = compute_signature(gaps)

    if previous_signature and signature == previous_signature:
        next_attempt = attempt + 1
    else:
        next_attempt = 1

    status = "STUCK" if next_attempt >= STUCK_THRESHOLD else "FAIL"
    return status, signature, next_attempt
