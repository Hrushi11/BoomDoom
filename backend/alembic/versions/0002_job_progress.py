"""job.progress: the current step of a running job (film: voice, plan, code, review, render, compose)

Shown on /trackVideoGen.

Revision ID: 0002
Revises: 0001
"""

from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("ALTER TABLE job ADD COLUMN IF NOT EXISTS progress TEXT")


def downgrade() -> None:
    op.execute("ALTER TABLE job DROP COLUMN IF EXISTS progress")
