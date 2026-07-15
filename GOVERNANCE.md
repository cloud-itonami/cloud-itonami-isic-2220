# Governance

`cloud-itonami-isic-2220` is an OSS open-business blueprint for
injection-molded-plastics-products manufacturing enablement,
robotics-premised.

## Maintainers

Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Clamp-Force Governor remains independent of the Molding Advisor.
- hard policy violations (force-ship, record-suppression, fabricated
  material-spec citation) cannot be overridden by human approval.
- every shipment, certificate issuance, hold and disclosure path is
  auditable.
- sensitive plant, formulation and personal data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust
model, storage contract, public business model, operator
certification or license should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification
is a separate trust mark and should require security, robot-safety,
audit and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or material-spec-evidence policy checks
- mishandling sensitive plant or personal data
- misrepresenting certification status
- failing to respond to security or product-safety incidents
