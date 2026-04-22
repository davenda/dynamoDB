#!/bin/zsh
cd /Users/dave/Claude/DynamoDBPlugin
git add -A
git commit -F - <<'COMMITMSG'
feat: v1.0.1 - marketplace release

- Add plugin marketplace icons (pluginIcon.svg/dark) for light/dark themes
- Add theme-aware toolbar and tree icons (dynamodb.png/dark)
- Add Native DynamoDB Query mode with radio toggle (SQL / DynamoDB Query)
- Support FilterExpression, KeyConditionExpression, attribute names/values
- Fix SQL WHERE clause support (city = 'Tampa' style queries)
- Fix pagination: respect LIMIT clause, grey out Next when limit reached
- Fix Rows Per Page selector
- Remove auto-load-more on scroll; replace with proper pagination controls
- Remove GSI Analyzer, Entity Facets, and AI Query tabs
- Remove Dry Run option from query runner
- Add expand/collapse all buttons next to plugin title
- Add solid dividers between toolbar icons and title bar
- Update About dialog with author info and website (https://dynamodbpro.vercel.app)
- Set sinceBuild to 243 for broader IntelliJ version compatibility
- Bump version to 1.0.1
COMMITMSG

