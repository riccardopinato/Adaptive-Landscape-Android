#!/usr/bin/env python3
"""GitHub-only Deskland development heartbeat.

Runs on GitHub's default branch each hour. It DOES NOT restart ChatGPT, edit
app code, bypass gates or assert real-device QA. Issues are idempotent.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

API = "https://api.github.com"
ISSUE_TITLE = "Deskland — review interrupted development"
MARKER = "<!-- deskland-hourly-watch-v1 -->"
STALLED_HOURS = 4
CI_RUNNING_HOURS = 2


def api(path: str, method: str = "GET", payload: dict | None = None):
    token = os.environ.get("GH_TOKEN", "")
    if not token:
        raise RuntimeError("No GitHub token")
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    request = urllib.request.Request(
        API + path,
        data=body,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": "Bearer " + token,
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "Deskland-Development-Watch",
            **({"Content-Type": "application/json"} if body is not None else {}),
        },
        method=method,
    )
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.loads(response.read().decode("utf-8"))


def parsed_date(value: str) -> dt.datetime:
    return dt.datetime.fromisoformat(value.replace("Z", "+00:00"))


def investigate(repo: str):
    pulls = api(f"/repos/{repo}/pulls?state=open&per_page=100")
    relevant = [
        pr for pr in pulls
        if (pr.get("head", {}).get("ref", "").startswith(("feat/auto-home", "fix/deskland"))
            or pr.get("number") in (23, 24))
    ]
    if not relevant:
        return [], "No active Deskland audit PR was found. Verify the 13 audit gates manually."
    relevant.sort(key=lambda pr: pr["updated_at"], reverse=True)
    primary = relevant[0]
    sha = primary["head"]["sha"]
    commit = api(f"/repos/{repo}/commits/{sha}")
    committed_at = parsed_date(commit["commit"]["committer"]["date"])
    now = dt.datetime.now(dt.timezone.utc)
    idle_hours = max(0.0, (now - committed_at).total_seconds() / 3600.0)
    runs = api(f"/repos/{repo}/actions/runs?head_sha={urllib.parse.quote(sha)}&per_page=30")
    runs = [
        run for run in runs.get("workflow_runs", [])
        if run.get("name") != "Deskland hourly progress watch"
    ]
    runs.sort(key=lambda run: run["created_at"], reverse=True)
    issues = []
    if not runs and idle_hours >= 2:
        issues.append("No development CI run found for the current head SHA.")
    elif runs:
        latest = runs[0]
        status = latest["status"]
        conclusion = latest.get("conclusion")
        age = (now - parsed_date(latest["created_at"])).total_seconds() / 3600.0
        if status == "completed" and conclusion not in ("success", "skipped", "neutral"):
            issues.append(f"CI completed with {conclusion}: {latest['html_url']}")
        elif status in ("queued", "in_progress", "waiting") and age > CI_RUNNING_HOURS:
            issues.append(f"CI has stayed {status} for more than {CI_RUNNING_HOURS} hours: {latest['html_url']}")
    if idle_hours >= STALLED_HOURS:
        issues.append(
            f"No new source commit for {int(idle_hours)} hours on PR #{primary['number']}; "
            "this is an inactivity warning, not proof a ChatGPT session failed."
        )
    summary = (
        f"Checked PR #{primary['number']} at \\u0060{sha}\\u0060. "
        f"Last commit: {committed_at.isoformat()}. "
        f"Latest CI: {runs[0]['status'] if runs else 'none'}. "
        "The 13 audit issues remain open until individually tested, "
        "including physical Android launcher/renderer QA."
    )
    return issues, summary


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    problems, summary = investigate(repo)
    all_issues = api(f"/repos/{repo}/issues?state=all&per_page=100")
    existing = next((issue for issue in all_issues
                     if issue.get("title") == ISSUE_TITLE
                     and "pull_request" not in issue), None)
    if problems:
        message = "\\n".join("- " + item for item in problems)
        body = (
            MARKER + "\\n" + "### Monitoring detected a development blocker\\n"
            + message + "\\n\\n" + summary
            + "\\n\\nThe monitor only checks GitHub commits/CI. It cannot restart "
              "an interrupted ChatGPT or PC session, and it must not merge unverified changes."
        )
        if existing:
            update = {}
            if existing.get("state") != "open":
                update["state"] = "open"
            if existing.get("body") != body:
                update["body"] = body
            if update:
                api(f"/repos/{repo}/issues/{existing['number']}", "PATCH", update)
        else:
            api(f"/repos/{repo}/issues", "POST", {"title": ISSUE_TITLE, "body": body})
        print("BLOCKED: " + "; ".join(problems))
        # Always succeed: an issue is the durable alert, rather than a failed cron job.
    else:
        if existing and existing.get("state") == "open":
            api(f"/repos/{repo}/issues/{existing['number']}", "PATCH",
                {"state": "closed", "state_reason": "completed"})
        print("NO NEW BLOCKERS: " + summary)


if __name__ == "__main__":
    try:
        main()
    except (KeyError, ValueError, urllib.error.HTTPError, urllib.error.URLError,
            RuntimeError) as error:
        print(f"Monitoring failed: {error}", file=sys.stderr)
        sys.exit(1)
