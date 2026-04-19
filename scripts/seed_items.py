#!/usr/bin/env python3
"""Seed test items into the AppData table on DynamoDB Local."""
import urllib.request, urllib.error, json, sys

ENDPOINT = "http://localhost:8000"
TABLE    = "AppData"

# Fake auth header — DynamoDB Local accepts any value
AUTH = "AWS4-HMAC-SHA256 Credential=local/20260101/us-east-1/dynamodb/aws4_request, SignedHeaders=host, Signature=fake"

def call(target, body):
    data = json.dumps(body).encode()
    req  = urllib.request.Request(
        ENDPOINT,
        data    = data,
        headers = {
            "Content-Type"  : "application/x-amz-json-1.0",
            "X-Amz-Target"  : f"DynamoDB_20120810.{target}",
            "Authorization" : AUTH,
        },
        method = "POST",
    )
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def put(item):
    call("PutItem", {"TableName": TABLE, "Item": item})

# ── Users ─────────────────────────────────────────────────────────────────────
put({"PK":{"S":"USER#alice"},  "SK":{"S":"PROFILE"},
     "GSI1PK":{"S":"USERS"},   "GSI1SK":{"S":"alice"},
     "name":{"S":"Alice Smith"},"email":{"S":"alice@example.com"},
     "role":{"S":"admin"},      "createdAt":{"S":"2025-01-10T09:00:00Z"}})
print("  ✔ USER#alice")

put({"PK":{"S":"USER#bob"},    "SK":{"S":"PROFILE"},
     "GSI1PK":{"S":"USERS"},   "GSI1SK":{"S":"bob"},
     "name":{"S":"Bob Jones"}, "email":{"S":"bob@example.com"},
     "role":{"S":"member"},    "createdAt":{"S":"2025-02-14T12:30:00Z"}})
print("  ✔ USER#bob")

put({"PK":{"S":"USER#carol"},  "SK":{"S":"PROFILE"},
     "GSI1PK":{"S":"USERS"},   "GSI1SK":{"S":"carol"},
     "name":{"S":"Carol White"},"email":{"S":"carol@example.com"},
     "role":{"S":"member"},    "createdAt":{"S":"2025-03-01T08:15:00Z"}})
print("  ✔ USER#carol")

# ── Orders ────────────────────────────────────────────────────────────────────
put({"PK":{"S":"USER#alice"},      "SK":{"S":"ORDER#1001"},
     "GSI1PK":{"S":"ORDER#1001"},  "GSI1SK":{"S":"USER#alice"},
     "status":{"S":"DELIVERED"},   "total":{"N":"129.99"},
     "items":{"N":"3"},            "placedAt":{"S":"2025-06-01T14:22:00Z"}})
print("  ✔ ORDER#1001 (alice)")

put({"PK":{"S":"USER#alice"},      "SK":{"S":"ORDER#1002"},
     "GSI1PK":{"S":"ORDER#1002"},  "GSI1SK":{"S":"USER#alice"},
     "status":{"S":"SHIPPED"},     "total":{"N":"49.50"},
     "items":{"N":"1"},            "placedAt":{"S":"2025-09-15T10:00:00Z"}})
print("  ✔ ORDER#1002 (alice)")

put({"PK":{"S":"USER#bob"},        "SK":{"S":"ORDER#1003"},
     "GSI1PK":{"S":"ORDER#1003"},  "GSI1SK":{"S":"USER#bob"},
     "status":{"S":"PENDING"},     "total":{"N":"299.00"},
     "items":{"N":"5"},            "placedAt":{"S":"2026-01-20T16:45:00Z"}})
print("  ✔ ORDER#1003 (bob)")

put({"PK":{"S":"USER#carol"},      "SK":{"S":"ORDER#1004"},
     "GSI1PK":{"S":"ORDER#1004"},  "GSI1SK":{"S":"USER#carol"},
     "status":{"S":"DELIVERED"},   "total":{"N":"89.00"},
     "items":{"N":"2"},            "placedAt":{"S":"2026-02-10T09:30:00Z"}})
print("  ✔ ORDER#1004 (carol)")

# ── Products ──────────────────────────────────────────────────────────────────
put({"PK":{"S":"PRODUCT#widget-pro"}, "SK":{"S":"METADATA"},
     "GSI1PK":{"S":"PRODUCTS"},       "GSI1SK":{"S":"widget-pro"},
     "name":{"S":"Widget Pro"},       "price":{"N":"49.99"},
     "stock":{"N":"142"},             "category":{"S":"widgets"}})
print("  ✔ PRODUCT#widget-pro")

put({"PK":{"S":"PRODUCT#gadget-x"},   "SK":{"S":"METADATA"},
     "GSI1PK":{"S":"PRODUCTS"},       "GSI1SK":{"S":"gadget-x"},
     "name":{"S":"Gadget X"},         "price":{"N":"129.00"},
     "stock":{"N":"37"},              "category":{"S":"gadgets"}})
print("  ✔ PRODUCT#gadget-x")

put({"PK":{"S":"PRODUCT#super-gizmo"},"SK":{"S":"METADATA"},
     "GSI1PK":{"S":"PRODUCTS"},       "GSI1SK":{"S":"super-gizmo"},
     "name":{"S":"Super Gizmo"},      "price":{"N":"299.00"},
     "stock":{"N":"8"},               "category":{"S":"gadgets"}})
print("  ✔ PRODUCT#super-gizmo")

# ── Verify ────────────────────────────────────────────────────────────────────
resp  = call("Scan", {"TableName": TABLE, "Select": "ALL_ATTRIBUTES"})
items = resp.get("Items", [])

from collections import Counter
counts = Counter(it["PK"]["S"].split("#")[0] for it in items)
print()
print("  Item counts:")
for entity, n in sorted(counts.items()):
    print(f"    {entity:<12} {n}")
print(f"    {'─'*18}")
print(f"    Total        {len(items)}")
print()
print(f"✅  AppData table seeded on {ENDPOINT}")
print(f"    Keys  : PK (HASH) / SK (RANGE)")
print(f"    GSI   : GSI1  →  GSI1PK / GSI1SK")
print()
print("  Connect the plugin with:")
print("    Endpoint : http://localhost:8000")
print("    Region   : us-east-1")
print("    Cred type: DEFAULT_CHAIN  (any value works for local)")

