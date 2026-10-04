#!/usr/bin/env python3
"""Manual probe: which Gemini / Groq models accept the structured-output
formats the app asks for. NOT part of CI -- it spends real quota and depends
on live services. Run it by hand, with your own keys:

    GEMINI_API_KEY=... GROQ_API_KEY=... python3 scripts/probe_structured_output.py
    python3 scripts/probe_structured_output.py --gemini-model gemini-3.6-flash --groq-model qwen/qwen3.6-27b

For each model it sends one tiny request per enforcement level the app uses
(schema -> JSON only) and prints whether it was accepted, and if not, the
provider's own error message -- the text the app's "format unsupported"
detection (shared/.../domain/ai/ResponseFormatSupport.kt) reads. Use it to
confirm that detection matches what the providers really say.
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

# The shape that matters: a typed object, a list, and a free-key map
# (the app's "titles" / nutrient maps) -- the part most likely to be refused.
SCHEMA = {
    "type": "object",
    "properties": {
        "titles": {"type": "object", "additionalProperties": {"type": "string"}},
        "items": {"type": "array", "items": {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}},
    },
    "required": ["titles", "items"],
}
PROMPT = 'Return JSON: "titles" maps "items" to its translation in French; "items" has two fruits with a "name".'


def post(url, body, headers):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json", **headers})
    try:
        with urllib.request.urlopen(req, timeout=90) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def error_message(body):
    try:
        return json.loads(body).get("error", {}).get("message", body[:200])
    except ValueError:
        return body[:200]


def probe_gemini(key, model):
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}"
    levels = {
        "SCHEMA": {"responseMimeType": "application/json", "responseJsonSchema": SCHEMA},
        "JSON_ONLY": {"responseMimeType": "application/json"},
    }
    for level, config in levels.items():
        status, body = post(url, {"contents": [{"parts": [{"text": PROMPT}]}], "generationConfig": config}, {})
        print(f"gemini  {model:45} {level:10} HTTP {status}  {'ok' if status == 200 else error_message(body)}")


def groq_models(key):
    req = urllib.request.Request("https://api.groq.com/openai/v1/models", headers={"Authorization": f"Bearer {key}"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return [m["id"] for m in json.loads(r.read())["data"]]


def probe_groq(key, model):
    url = "https://api.groq.com/openai/v1/chat/completions"
    levels = {
        "SCHEMA": {"type": "json_schema", "json_schema": {"name": "probe", "schema": SCHEMA}},
        "JSON_ONLY": {"type": "json_object"},
    }
    for level, fmt in levels.items():
        body = {"model": model, "max_tokens": 300, "messages": [{"role": "user", "content": PROMPT}], "response_format": fmt}
        status, text = post(url, body, {"Authorization": f"Bearer {key}"})
        print(f"groq    {model:45} {level:10} HTTP {status}  {'ok' if status == 200 else error_message(text)}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gemini-model", action="append", default=None)
    ap.add_argument("--groq-model", action="append", default=None, help="default: every model the key can see")
    args = ap.parse_args()

    gemini_key, groq_key = os.environ.get("GEMINI_API_KEY"), os.environ.get("GROQ_API_KEY")
    if not gemini_key and not groq_key:
        sys.exit("set GEMINI_API_KEY and/or GROQ_API_KEY")
    if gemini_key:
        for model in args.gemini_model or ["gemini-3.6-flash"]:
            probe_gemini(gemini_key, model)
    if groq_key:
        for model in args.groq_model or groq_models(groq_key):
            probe_groq(groq_key, model)


if __name__ == "__main__":
    main()
