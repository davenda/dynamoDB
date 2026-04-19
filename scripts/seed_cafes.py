#!/usr/bin/env python3
"""Seed 300 cafe items into the Cafe-us-east-1 table on AWS DynamoDB."""
import boto3
import random
from datetime import datetime, timedelta

TABLE  = "Cafe-us-east-1"
REGION = "us-east-1"

# ── Data pools ────────────────────────────────────────────────────────────────
CAFE_ADJECTIVES = [
    "Sunrise", "Moonlight", "Golden", "Silver", "Blue", "Green", "Urban",
    "Rustic", "Cozy", "Artisan", "Harvest", "Amber", "Crimson", "Velvet",
    "Maple", "Cedar", "Misty", "Brewed", "Roasted", "Steamy", "Vintage",
    "Nordic", "Coastal", "Garden", "Breezy", "Ember", "Copper", "Stone",
    "Willow", "Birch", "Summit", "Harbor", "Dusk", "Dawn", "Prism",
]
CAFE_NOUNS = [
    "Cafe", "Espresso", "Brew", "Roast", "Grind", "Press", "Drip",
    "Bean", "Cup", "Mug", "Latte", "Mocha", "Blend", "Pour", "Steam",
    "Perch", "Nook", "Den", "Corner", "House", "Place", "Spot", "Bar",
    "Workshop", "Lab", "Studio", "Lounge", "Room", "Co.", "Collective",
]
CITIES = [
    "New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia",
    "San Antonio", "San Diego", "Dallas", "San Jose", "Austin", "Jacksonville",
    "Fort Worth", "Columbus", "Charlotte", "Indianapolis", "San Francisco",
    "Seattle", "Denver", "Washington DC", "Nashville", "Oklahoma City",
    "El Paso", "Boston", "Las Vegas", "Portland", "Memphis", "Louisville",
    "Baltimore", "Milwaukee", "Albuquerque", "Tucson", "Fresno", "Sacramento",
    "Mesa", "Kansas City", "Atlanta", "Omaha", "Colorado Springs", "Raleigh",
    "Miami", "Oakland", "Minneapolis", "Tulsa", "Tampa", "Arlington",
    "New Orleans", "Wichita", "Cleveland", "Bakersfield",
]
SPECIALTIES = [
    "Pour Over", "Cold Brew", "Espresso Bar", "Nitro Coffee", "Latte Art",
    "Single Origin", "Batch Brew", "Siphon Coffee", "Chemex", "AeroPress",
    "French Press", "Flat White", "Cortado", "Macchiato", "Affogato",
]

def random_date(start_year=2020, end_year=2026):
    start = datetime(start_year, 1, 1)
    end   = datetime(end_year, 4, 1)
    delta = end - start
    return (start + timedelta(days=random.randint(0, delta.days))).strftime("%Y-%m-%dT%H:%M:%SZ")

def generate_cafe(index: int) -> dict:
    adj  = random.choice(CAFE_ADJECTIVES)
    noun = random.choice(CAFE_NOUNS)
    name = f"{adj} {noun}"
    city = random.choice(CITIES)
    return {
        "cafe_id":   {"S": f"CAFE#{index:04d}"},
        "cafe_name": {"S": name},
        "city":      {"S": city},
        "rating":    {"N": str(round(random.uniform(3.5, 5.0), 1))},
        "created_at":{"S": random_date()},
        "specialty": {"S": random.choice(SPECIALTIES)},
        "seats":     {"N": str(random.randint(10, 120))},
        "wifi":      {"BOOL": random.choice([True, False])},
        "open_24h":  {"BOOL": random.choice([True, False])},
        "price_tier":{"S": random.choice(["$", "$$", "$$$"])},
    }

# ── Seed ──────────────────────────────────────────────────────────────────────
client = boto3.client("dynamodb", region_name=REGION)

BATCH_SIZE = 25   # DynamoDB BatchWriteItem max
total_written = 0

cafes = [generate_cafe(i + 4) for i in range(300)]   # start at CAFE#0004 to avoid collision

print(f"▶ Seeding {len(cafes)} cafes to '{TABLE}' in {REGION}…")

for batch_start in range(0, len(cafes), BATCH_SIZE):
    batch = cafes[batch_start:batch_start + BATCH_SIZE]
    request_items = {
        TABLE: [{"PutRequest": {"Item": item}} for item in batch]
    }
    resp = client.batch_write_item(RequestItems=request_items)
    # Handle unprocessed items (retry once)
    unprocessed = resp.get("UnprocessedItems", {})
    if unprocessed:
        resp2 = client.batch_write_item(RequestItems=unprocessed)
        leftover = resp2.get("UnprocessedItems", {})
        if leftover:
            print(f"  ⚠  {len(leftover.get(TABLE, []))} items still unprocessed after retry")
    written = len(batch) - len(unprocessed.get(TABLE, []))
    total_written += written
    print(f"  ✔ {total_written:>3} / {len(cafes)} cafes written  (batch {batch_start // BATCH_SIZE + 1})")

print()
print(f"✅  Done!  {total_written} cafes seeded into '{TABLE}'.")

