# Changelog

## v0.1.0

First release.

- **Analysis engine.** Analyses images and PDFs with AWS Bedrock vision models: Amazon Nova (default `amazon.nova-pro-v1:0`) or Anthropic Claude.
- **File handling.** PDFs are converted to page images (the first 3 pages by default). Images are resized and re-encoded as JPEG.
- **Structured result.** Each analysis returns:
  - a score
  - a traffic light worked out on the server
  - document-type, legibility and expiry checks
  - a comparison of record values against what the document says
  - findings
  - a suggested decision with a reason
- **Assistance modes.** Off / Hint / Full, applied on the server. `AssistModeResolver` lets you plug in per-user modes.
- **Safety.** Text inside a document is never treated as an instruction to the AI. Logs contain no personal data. Errors return a safe message instead of failing.
- **REST API.** `GET /api/status` and `POST /api/analyse` (multipart).
- **React components.** The `AiAssistPanel` and `AiTrafficDot` components, with TypeScript types (source in `frontend/`).

### Running the release JAR

Requires Java 21 and AWS credentials allowed to call `bedrock:InvokeModel`:

```bash
DOCVERIFY_BEDROCK_ENABLED=true DOCVERIFY_BEDROCK_REGION=eu-west-2 \
  java -jar document-verification-assistant-0.1.0.jar
```

See the README for configuration, the API and cost.
