#!/usr/bin/env zsh
# ─────────────────────────────────────────────────────────────────────────────
# seed-local.sh  —  Start DynamoDB Local + create + seed a test table
# Usage:  ./scripts/seed-local.sh
# ─────────────────────────────────────────────────────────────────────────────
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$SCRIPT_DIR/.."
DYNAMO_DIR="$ROOT/local-dynamo"
PORT=8000
ENDPOINT="http://localhost:$PORT"

# ── Fake AWS creds required by the SDK (any value works for local) ────────────
export AWS_ACCESS_KEY_ID=local
export AWS_SECRET_ACCESS_KEY=local
export AWS_DEFAULT_REGION=us-east-1

# ── 1. Start DynamoDB Local (if not already running) ─────────────────────────
if lsof -iTCP:$PORT -sTCP:LISTEN -t &>/dev/null; then
  echo "✔ DynamoDB Local already running on port $PORT"
else
  echo "▶ Starting DynamoDB Local on port $PORT..."
  java -Djava.library.path="$DYNAMO_DIR/DynamoDBLocal_lib" \
       -jar "$DYNAMO_DIR/DynamoDBLocal.jar" \
       -sharedDb -inMemory -port $PORT &
  DYNAMO_PID=$!
  echo "  PID=$DYNAMO_PID"
  # Wait for it to accept connections
  for i in {1..15}; do
    if lsof -iTCP:$PORT -sTCP:LISTEN -t &>/dev/null; then
      echo "  ✔ Ready"
      break
    fi
    sleep 1
  done
fi

# ── Helper: run an AWS CLI-style call via curl ────────────────────────────────
dynamo() {
  curl -s -X POST "$ENDPOINT" \
    -H "Content-Type: application/x-amz-json-1.0" \
    -H "X-Amz-Target: DynamoDB_20120810.$1" \
    -d "$2"
}

# ── 2. Create table (single-table design) ────────────────────────────────────
echo ""
echo "▶ Creating table 'AppData'..."
dynamo CreateTable '{
  "TableName": "AppData",
  "BillingMode": "PAY_PER_REQUEST",
  "AttributeDefinitions": [
    {"AttributeName":"PK","AttributeType":"S"},
    {"AttributeName":"SK","AttributeType":"S"},
    {"AttributeName":"GSI1PK","AttributeType":"S"},
    {"AttributeName":"GSI1SK","AttributeType":"S"}
  ],
  "KeySchema": [
    {"AttributeName":"PK","KeyType":"HASH"},
    {"AttributeName":"SK","KeyType":"RANGE"}
  ],
  "GlobalSecondaryIndexes": [
    {
      "IndexName": "GSI1",
      "KeySchema": [
        {"AttributeName":"GSI1PK","KeyType":"HASH"},
        {"AttributeName":"GSI1SK","KeyType":"RANGE"}
      ],
      "Projection": {"ProjectionType":"ALL"}
    }
  ]
}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('  ✔ Created:', d.get('TableDescription',{}).get('TableName','(see response)')) if 'TableDescription' in d else print('  ⚠', d)"

# ── 3. Seed items ─────────────────────────────────────────────────────────────
echo ""
echo "▶ Seeding items..."

seed_item() {
  local desc="$1"
  local item="$2"
  result=$(dynamo PutItem "{\"TableName\":\"AppData\",\"Item\":$item}")
  echo "  ✔ $desc"
}

# Users
seed_item "USER#alice" '{
  "PK":{"S":"USER#alice"},   "SK":{"S":"PROFILE"},
  "GSI1PK":{"S":"USERS"},    "GSI1SK":{"S":"alice"},
  "name":{"S":"Alice Smith"}, "email":{"S":"alice@example.com"},
  "role":{"S":"admin"},       "createdAt":{"S":"2025-01-10T09:00:00Z"}
}'

seed_item "USER#bob" '{
  "PK":{"S":"USER#bob"},     "SK":{"S":"PROFILE"},
  "GSI1PK":{"S":"USERS"},    "GSI1SK":{"S":"bob"},
  "name":{"S":"Bob Jones"},  "email":{"S":"bob@example.com"},
  "role":{"S":"member"},     "createdAt":{"S":"2025-02-14T12:30:00Z"}
}'

seed_item "USER#carol" '{
  "PK":{"S":"USER#carol"},   "SK":{"S":"PROFILE"},
  "GSI1PK":{"S":"USERS"},    "GSI1SK":{"S":"carol"},
  "name":{"S":"Carol White"},"email":{"S":"carol@example.com"},
  "role":{"S":"member"},     "createdAt":{"S":"2025-03-01T08:15:00Z"}
}'

# Orders
seed_item "ORDER#1001 (alice)" '{
  "PK":{"S":"USER#alice"},        "SK":{"S":"ORDER#1001"},
  "GSI1PK":{"S":"ORDER#1001"},    "GSI1SK":{"S":"USER#alice"},
  "status":{"S":"DELIVERED"},     "total":{"N":"129.99"},
  "items":{"N":"3"},              "placedAt":{"S":"2025-06-01T14:22:00Z"}
}'

seed_item "ORDER#1002 (alice)" '{
  "PK":{"S":"USER#alice"},        "SK":{"S":"ORDER#1002"},
  "GSI1PK":{"S":"ORDER#1002"},    "GSI1SK":{"S":"USER#alice"},
  "status":{"S":"SHIPPED"},       "total":{"N":"49.50"},
  "items":{"N":"1"},              "placedAt":{"S":"2025-09-15T10:00:00Z"}
}'

seed_item "ORDER#1003 (bob)" '{
  "PK":{"S":"USER#bob"},          "SK":{"S":"ORDER#1003"},
  "GSI1PK":{"S":"ORDER#1003"},    "GSI1SK":{"S":"USER#bob"},
  "status":{"S":"PENDING"},       "total":{"N":"299.00"},
  "items":{"N":"5"},              "placedAt":{"S":"2026-01-20T16:45:00Z"}
}'

# Products
seed_item "PRODUCT#widget-pro" '{
  "PK":{"S":"PRODUCT#widget-pro"}, "SK":{"S":"METADATA"},
  "GSI1PK":{"S":"PRODUCTS"},       "GSI1SK":{"S":"widget-pro"},
  "name":{"S":"Widget Pro"},       "price":{"N":"49.99"},
  "stock":{"N":"142"},             "category":{"S":"widgets"}
}'

seed_item "PRODUCT#gadget-x" '{
  "PK":{"S":"PRODUCT#gadget-x"},   "SK":{"S":"METADATA"},
  "GSI1PK":{"S":"PRODUCTS"},       "GSI1SK":{"S":"gadget-x"},
  "name":{"S":"Gadget X"},         "price":{"N":"129.00"},
  "stock":{"N":"37"},              "category":{"S":"gadgets"}
}'

# ── 4. Verify ─────────────────────────────────────────────────────────────────
echo ""
echo "▶ Table info:"
dynamo DescribeTable '{"TableName":"AppData"}' \
  | python3 -c "
import sys, json
d = json.load(sys.stdin)['Table']
gsis = d.get('GlobalSecondaryIndexes', [])
print(f'  Table  : {d[\"TableName\"]}')
print(f'  Status : {d[\"TableStatus\"]}')
print(f'  Keys   : PK (HASH)  SK (RANGE)')
print(f'  GSIs   : {[g[\"IndexName\"] for g in gsis]}')
"

echo ""
echo "▶ Item count per entity type:"
dynamo Scan '{"TableName":"AppData","Select":"ALL_ATTRIBUTES"}' \
  | python3 -c "
import sys, json, collections
items = json.load(sys.stdin).get('Items', [])
counts = collections.Counter()
for it in items:
    pk = it['PK']['S']
    counts[pk.split('#')[0]] += 1
for k,v in sorted(counts.items()):
    print(f'  {k:<12} {v} item(s)')
print(f'  {\"─\"*20}')
print(f'  Total        {len(items)} item(s)')
"

echo ""
echo "✅ Done!  Connect the plugin to:"
echo "   Endpoint : http://localhost:$PORT"
echo "   Region   : us-east-1"
echo "   Table    : AppData"

