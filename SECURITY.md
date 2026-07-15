# Security Policy

This project handles injection-molding-plant manufacturing,
material-spec-certification and end-of-line quality-conformance
workflows. Treat vulnerabilities as potentially high impact even when
the demo data is synthetic -- a bypassed clamp-force/material-spec
gate on real hardware risks a mold-clamp mechanical failure or a
part-flash/short-shot safety-relevant defect reaching a downstream
assembler, not merely a data-integrity issue.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real plant, supplier or personnel data exposure
- authorization bypass
- Clamp-Force Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- tenant isolation failures

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on molding-run-batch records, policy enforcement or audit
  logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real plant/production data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
