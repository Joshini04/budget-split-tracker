# Splitz & Co. — Trip Expense Splitter

A full-stack expense-splitting app for trips and group hangouts. Add people to a tab, log expenses, split costs unevenly if needed, and instantly see who owes whom.

**Live demo:** [add your deployed link here once live]
**Frontend repo:** this repo, `frontend/index.html`

## Tech Stack

- **Backend:** Java 17, Spring Boot, Spring Data JPA
- **Database:** H2 (embedded, file-based)
- **Frontend:** Plain HTML, CSS, JavaScript (no framework)
- **Hosting:** Render (backend), GitHub Pages (frontend)

## Features

- Create a group ("tab") for a trip or hangout
- Add members to the group
- Log expenses with a description, amount, and who paid
- **Custom splitting** — choose exactly which members each expense should be split among (not just an even split across everyone)
- Automatic balance calculation (who's owed money, who owes money)
- Automatic settlement suggestions (who should pay whom, and how much)
- Input validation with clear error messages

## Data Model

Three related tables:

- **Group** — a trip/tab (`id`, `name`)
- **Member** — a person in a group (`id`, `name`, linked to one `Group`)
- **Expense** — a cost logged in a group (`id`, `description`, `amount`, linked to a `Group` and the `Member` who paid)
- **ExpenseSplit** — a join table linking each `Expense` to the specific `Member`s it should be split among (enables custom/unequal participation per expense)

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/groups` | Create a new group. Body: `{ "name": "Goa Trip" }` |
| GET | `/api/groups` | List all groups |
| POST | `/api/groups/{groupId}/members` | Add a member to a group. Body: `{ "name": "Priya" }` |
| GET | `/api/groups/{groupId}/members` | List members in a group |
| POST | `/api/groups/{groupId}/expenses` | Log an expense. Body: `{ "description": "Hotel", "amount": 2000, "paidById": 1, "splitAmong": [1, 2] }` (`splitAmong` is optional — defaults to splitting among all group members) |
| GET | `/api/groups/{groupId}/expenses` | List expenses in a group |
| GET | `/api/groups/{groupId}/balances` | Get each member's net balance (positive = owed money, negative = owes money) |
| GET | `/api/groups/{groupId}/settlement` | Get a list of suggested payments to settle all balances |

### Example: full flow with curl

```bash
# Create a group
curl -X POST http://localhost:8080/api/groups -H "Content-Type: application/json" -d "{\"name\":\"Goa Trip\"}"

# Add members
curl -X POST http://localhost:8080/api/groups/1/members -H "Content-Type: application/json" -d "{\"name\":\"Priya\"}"

# Log an expense, split only between specific members
curl -X POST http://localhost:8080/api/groups/1/expenses -H "Content-Type: application/json" -d "{\"description\":\"Hotel\",\"amount\":2000,\"paidById\":1,\"splitAmong\":[1,2]}"

# See who owes whom
curl http://localhost:8080/api/groups/1/settlement
```

## Running Locally

```bash
mvnw spring-boot:run
```
Server runs on `http://localhost:8080`. Open `frontend/index.html` directly in a browser to use the UI.

## Known Limitations

- H2 database is file-based and not persistent on Render's free tier — data resets when the service spins down after inactivity. A production version would use a persistent database like PostgreSQL.
- Settlement algorithm uses a greedy matching approach — correct, but not guaranteed to produce the mathematically minimum number of transactions in every case.

## What I'd Improve With More Time

- Unit tests for balance calculation logic
- Persistent database for production use
- Authentication so groups are private to their members