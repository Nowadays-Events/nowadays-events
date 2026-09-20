import sqlite3
import unittest

from nowadays_agent import ensure_optional_columns


class DatabaseMigrationTests(unittest.TestCase):
    def test_adds_every_optional_column_to_legacy_database(self):
        connection = sqlite3.connect(":memory:")
        connection.execute(
            """
            CREATE TABLE events (
                external_id TEXT PRIMARY KEY,
                title TEXT NOT NULL
            )
            """
        )

        ensure_optional_columns(connection)

        columns = {
            row[1]: row for row in connection.execute("PRAGMA table_info(events)")
        }
        self.assertTrue({
            "category", "price_type", "price_cents", "currency",
            "occurrence_count", "next_occurrence_at", "time_precision",
            "original_time_text", "schedule_type", "occurrence_starts", "schedule_reason",
        }.issubset(columns))
        self.assertEqual("COMMUNITY", columns["category"][4].strip("'"))
        self.assertEqual("unknown", columns["price_type"][4].strip("'"))
        self.assertEqual("EUR", columns["currency"][4].strip("'"))
        self.assertEqual("1", columns["occurrence_count"][4])


if __name__ == "__main__":
    unittest.main()
