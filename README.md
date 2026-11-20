# Document Verification Assistant

An AI assistant that helps human reviewers check uploaded documents: passports, ID cards, photos, medical certificates, professional certificates, payment proofs and application forms. It sends the document image to a vision model on **AWS Bedrock**. It returns:

- a confidence score
- a traffic light
- the fields it read, compared with the values you have on record
- a list of findings
- a suggested decision

The AI is **advisory only**: the reviewer always makes the final decision.

## Assistance modes

Each reviewer gets one of three modes:

| Mode | What the reviewer sees |
|---|---|
| **OFF** | No AI. The API refuses analysis requests (`403`). |
| **HINT** | Only a confidence score (0–100) and a traffic light: 🟢 *Looks good* / 🟡 *Check carefully* / 🔴 *Likely problem*. |
| **FULL** | Everything in Hint, plus:<ul><li>document-type, legibility and expiry checks</li><li>a *record vs document* field comparison</li><li>a list of findings</li><li>a suggested decision (`VERIFY` / `NOT_LEGIBLE` / `REJECT`) with a drafted reason</li></ul> |

The server applies the mode. A Hint user's response simply doesn't contain the detailed findings, so they can't be revealed from the browser.

## How it works

```
 Browser (React panel)             Backend (Spring Boot)                       AWS Bedrock
 ─────────────────────             ─────────────────────                       ───────────
 [Analyse with AI] ──multipart──▶  POST /api/analyse
                                    ├─ resolve reviewer's mode (OFF → 403)
                                    ├─ PDF → page images (PDFBox), resize → JPEG
                                    ├─ prompt + expected values ───────────▶  Nova Pro / Claude
                                    ├─ parse JSON leniently  ◀───────────────  (vision)
                                    ├─ compute traffic light server-side
                                    └─ filter by mode (HINT strips details)
 score / light / findings ◀──────  AssistView
```

Design points:

- **The server sets the traffic light.** The score and light come from the model's findings, not just its own verdict. A critical issue, the wrong document type, an illegible scan or an expired document always gives 🔴, and a "verify" suggestion is overridden.
- **Defence against prompt injection.** Text inside a document (for example *"ignore previous instructions"*) is treated as content to assess, never as an instruction, and is reported as a critical issue.
- **Privacy-aware logging.** The logs contain only a request ID, the model, sizes, token counts, cost and latency. They never contain prompts, model output, extracted values or expected values.
- **Errors never crash the call.** Throttling, access-denied, timeouts and unreadable output all come back as `status: "ERROR"` with a safe message.

## Repository layout

| Path | Contents |
|---|---|
| `backend/` | Spring Boot 3 / Java 21 service: the Bedrock analysis engine, Off/Hint/Full filtering and the REST API |
| `frontend/` | A React `AiAssistPanel` component, an `AiTrafficDot` indicator and TypeScript types for the API |

## Quick start

### Prerequisites

1. **Java 21 and Maven**, and Node 18+ for the frontend.
2. **An AWS account with Bedrock.** The default model, `amazon.nova-pro-v1:0`, needs no extra sign-up. Anthropic Claude models need a one-time use-case form in the Bedrock console.
3. **AWS credentials allowed to call `bedrock:InvokeModel`** on that model. Any of these works:
   - environment variables or an AWS profile
   - an EC2 or ECS role
   - `DOCVERIFY_BEDROCK_ACCESS_KEY` / `DOCVERIFY_BEDROCK_SECRET_KEY`

   Example IAM policy:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": "bedrock:InvokeModel",
       "Resource": "arn:aws:bedrock:eu-west-2::foundation-model/amazon.nova-pro-v1:0"
     }]
   }
   ```

### Run the backend

```bash
cd backend
DOCVERIFY_BEDROCK_ENABLED=true \
DOCVERIFY_BEDROCK_REGION=eu-west-2 \
DOCVERIFY_ASSIST_MODE=FULL \
mvn spring-boot:run
```

### Analyse a document

```bash
curl -F file=@passport.jpg \
     -F requirementCode=PASSPORT \
     -F requirementLabel="Passport photo page" \
     -F 'expectedFields={"fullName":"Jane Doe","dateOfBirth":"1990-01-31","passportNumber":"123456789"}' \
     http://localhost:8080/api/analyse
```

Example response in **FULL** mode:

```json
{
  "mode": "FULL",
  "status": "SUCCESS",
  "overallScore": 72,
  "trafficLight": "AMBER",
  "summary": "Valid passport photo page; glare partly obscures the MRZ.",
  "documentTypeMatches": true,
  "legible": true,
  "expiryStatus": "VALID",
  "extractedFields": { "passportNumber": "123456789", "surname": "DOE" },
  "fieldComparisons": [
    { "field": "passportNumber", "expected": "123456789", "found": "123456789", "match": true }
  ],
  "issues": [ { "severity": "WARNING", "message": "Glare over the machine readable zone" } ],
  "recommendedDecision": "NOT_LEGIBLE",
  "recommendedReason": "MRZ partially obscured; request a clearer scan."
}
```

The same call in **HINT** mode returns only `mode`, `status`, `overallScore` and `trafficLight`.

## API

| Endpoint | Description |
|---|---|
| `GET /api/status` | `{ mode, available, provider, modelId }` for the caller |
| `POST /api/analyse` (multipart) | Fields: `file` (image or PDF, required), `requirementCode` (required), `requirementLabel`, `requirementDescription`, `expectedFields` (a JSON object of strings), `referenceDate` (`yyyy-MM-dd`, defaults to today) |

**Requirement codes with built-in guidance:**

| Code | Document |
|---|---|
| `PASSPORT` | Passport |
| `ID_CARD`, `NATIONAL_ID`, `DRIVING_LICENCE` | Other identity documents |
| `PHOTO` | Passport-style photo |
| `MEDICAL` | Medical certificate |
| `CERTIFICATE`, `LICENCE`, `QUALIFICATION` | Certificates and qualifications |
| `PAYMENT_PROOF`, `FEE` | Proof of payment |
| `FORM`, `APPLICATION` | Application form |

Any other code gets generic guidance based on the label and description you send.

**Status codes:**

| Code | Meaning |
|---|---|
| `403` | Mode is OFF |
| `503` | Engine not enabled |
| `413` | File too large |
| `400` | Bad input |

## Per-user modes

Out of the box there is one mode for everyone (`DOCVERIFY_ASSIST_MODE`). In a real application, decide the mode from the **authenticated user's permissions** by supplying your own `AssistModeResolver`:

```java
@Bean
@Primary
AssistModeResolver perUserMode(MyUserPermissions permissions) {
    return request -> {
        String user = request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName();
        if (user == null) return AssistMode.OFF;
        if (permissions.has(user, "AI_ASSIST_FULL")) return AssistMode.FULL;
        if (permissions.has(user, "AI_ASSIST_HINT")) return AssistMode.HINT;
        return AssistMode.OFF;
    };
}
```

Never take the mode from a request parameter or header the client controls.

## Frontend panel

```tsx
import AiAssistPanel, { AiTrafficDot } from './AiAssistPanel';

<AiAssistPanel
  mode={status.mode}                 // from GET /api/status
  available={status.available}
  analysis={analysis}                // response of POST /api/analyse
  hasFile={!!selectedFile}
  running={isAnalysing}
  onAnalyse={runAnalysis}
  onApplyRecommendation={(decision, reason) => openDecisionDialog(decision, reason)}
/>
```

- The panel renders nothing in OFF mode.
- `onApplyRecommendation` should only *pre-fill* your normal decision flow; the reviewer still confirms it.
- Set `analysis.stale = true` when the file has been replaced since the analysis ran. This greys out the result and hides "apply".
- To theme it, set the CSS variables `--dva-bg`, `--dva-card`, `--dva-border`, `--dva-text` and `--dva-muted`.

## Configuration

Every setting can be set with an environment variable or in `application.yml`:

| Environment variable | Default | Purpose |
|---|---|---|
| `DOCVERIFY_ASSIST_MODE` | `FULL` | Mode used by the default resolver |
| `DOCVERIFY_BEDROCK_ENABLED` | `false` | Turns the AI engine on |
| `DOCVERIFY_BEDROCK_REGION` | `eu-west-2` | Bedrock region; choose one that meets your data-residency needs |
| `DOCVERIFY_BEDROCK_MODEL_ID` | `amazon.nova-pro-v1:0` | Any `amazon.nova-*` or `anthropic.*` / `<geo>.anthropic.*` vision model |
| `DOCVERIFY_BEDROCK_CREDENTIALS_SOURCE` | `default` | `instance-profile` makes it always use the EC2 role |
| `DOCVERIFY_BEDROCK_ACCESS_KEY` / `_SECRET_KEY` | *(empty)* | Optional static credentials |
| `DOCVERIFY_BEDROCK_MAX_FILE_BYTES` | `15728640` | Largest file accepted (15 MB) |
| `DOCVERIFY_BEDROCK_MAX_PAGES` | `3` | How many PDF pages are sent |
| `DOCVERIFY_BEDROCK_MAX_OUTPUT_TOKENS` | `1500` | Cap on the model's answer |
| `DOCVERIFY_BEDROCK_INPUT_COST_PER_1K_USD` | `0.0016` | Input token price used for cost estimates |
| `DOCVERIFY_BEDROCK_OUTPUT_COST_PER_1K_USD` | `0.0064` | Output token price used for cost estimates |

The default cost rates are double the Amazon Nova Pro list price, as a safety margin.

## Cost

Every analysis is billed per token by AWS. A typical one-page check uses about 3,500 input tokens and 700 output tokens:

| Model | Approximate cost per check |
|---|---|
| Amazon Nova Pro | ≈ $0.005 |
| Claude Sonnet-class | ≈ $0.015–0.04 |

Each response includes `estimatedCostUsd` and the token counts. Running analyses only when a reviewer clicks keeps costs predictable. Set an AWS Budgets alert as well.

## Security and privacy notes

- **Data leaves your server.** Document images go to Amazon Bedrock in the region you configure, so make sure that fits your data-protection obligations. Bedrock does not use your data to train models.
- **No access control is included.** Protect the API with your own authentication.
- **The output is guidance, not a decision.** Treat it as advice and keep a human in the loop.

## Running the tests

```bash
cd backend && mvn test
cd frontend && npm ci && npm test
```

## Copyright

Copyright © 2026 lizmathew-neo. All rights reserved.

This repository is published for viewing only. **No license is granted** to use, copy, modify or distribute this code without the author's written permission.
