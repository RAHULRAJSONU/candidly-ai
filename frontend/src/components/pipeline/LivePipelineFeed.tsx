import { useEffect, useState } from "react";
import { api } from "../../api/client";
import type { PipelineActivityItem, PipelineActivityStage } from "../../api/types";
import { Card, CardHeader } from "../ui/Card";

// Polling interval for the live feed - matches Autopilot.tsx's existing 5s-polling
// pattern (this repo has no WebSocket/SSE anywhere), just faster since this view is
// meant to feel "live" rather than a periodic status check.
const POLL_INTERVAL_MS = 2500;
const FEED_LIMIT = 75;
// Caps how many distinct postings this view tracks client-side so a long-running tab
// doesn't grow the map forever - keeps only the most recently-active postings.
const MAX_TRACKED_JOBS = 60;

const COLUMNS: { key: string; label: string; stages: PipelineActivityStage[] }[] = [
  { key: "discovered", label: "Discovered", stages: ["DISCOVERED"] },
  { key: "screened", label: "Screened", stages: ["SCREENED"] },
  { key: "enriched", label: "Enriched", stages: ["EXTRACTED", "EMBEDDED"] },
  { key: "scored", label: "Scored", stages: ["SCORED", "SHORTLISTED"] },
  { key: "tailoring", label: "Tailoring", stages: ["TAILORING"] },
  { key: "reviewed", label: "Reviewed", stages: ["APPROVED", "REJECTED"] },
];

// Monotonic progress order - a job only ever moves forward through this rail even if a
// later poll happens to surface an older event for it first.
const STAGE_RANK: Record<PipelineActivityStage, number> = {
  DISCOVERED: 0,
  SCREENED: 1,
  EXTRACTED: 2,
  EMBEDDED: 2,
  SCORED: 3,
  SHORTLISTED: 3,
  TAILORING: 4,
  APPROVED: 5,
  REJECTED: 5,
};

const STAGE_TONE: Record<PipelineActivityStage, string> = {
  DISCOVERED: "bg-slate-100 text-slate-600 ring-slate-200",
  SCREENED: "bg-blue-50 text-blue-700 ring-blue-200",
  EXTRACTED: "bg-indigo-50 text-indigo-700 ring-indigo-200",
  EMBEDDED: "bg-indigo-50 text-indigo-700 ring-indigo-200",
  SCORED: "bg-amber-50 text-amber-700 ring-amber-200",
  SHORTLISTED: "bg-purple-50 text-purple-700 ring-purple-200",
  TAILORING: "bg-cyan-50 text-cyan-700 ring-cyan-200",
  APPROVED: "bg-emerald-50 text-emerald-700 ring-emerald-200",
  REJECTED: "bg-rose-50 text-rose-700 ring-rose-200",
};

interface TrackedJob {
  jobPostingId: string;
  title: string;
  company: string;
  stage: PipelineActivityStage;
  rank: number;
  occurredAt: string;
}

function columnIndexForStage(stage: PipelineActivityStage): number {
  return COLUMNS.findIndex((c) => c.stages.includes(stage));
}

/** Merges a newly-polled batch into the tracked job map - each job only ever advances to
 * a higher stage rank, never regresses, so a stale/out-of-order poll can't rewind it. */
function foldEvents(prev: Map<string, TrackedJob>, items: PipelineActivityItem[]): Map<string, TrackedJob> {
  const next = new Map(prev);
  const oldestFirst = [...items].reverse();
  for (const item of oldestFirst) {
    if (!item.jobPostingId) continue;
    const rank = STAGE_RANK[item.stage];
    const existing = next.get(item.jobPostingId);
    if (existing && existing.rank > rank) continue;
    if (existing && existing.rank === rank && existing.occurredAt >= item.occurredAt) continue;
    next.set(item.jobPostingId, {
      jobPostingId: item.jobPostingId,
      title: item.jobTitle ?? "(posting no longer available)",
      company: item.company ?? "",
      stage: item.stage,
      rank,
      occurredAt: item.occurredAt,
    });
  }
  if (next.size > MAX_TRACKED_JOBS) {
    const trimmed = Array.from(next.values())
      .sort((a, b) => (a.occurredAt < b.occurredAt ? 1 : -1))
      .slice(0, MAX_TRACKED_JOBS);
    return new Map(trimmed.map((j) => [j.jobPostingId, j]));
  }
  return next;
}

export function LivePipelineFeed() {
  const [jobs, setJobs] = useState<Map<string, TrackedJob>>(new Map());
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    const poll = () => {
      api.audit
        .pipelineFeed(FEED_LIMIT)
        .then((items) => {
          if (cancelled) return;
          setJobs((prev) => foldEvents(prev, items));
          setError(null);
        })
        .catch((e) => {
          if (!cancelled) setError(e instanceof Error ? e.message : "failed to load");
        });
    };
    poll();
    const interval = setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  const jobList = Array.from(jobs.values());
  const columnsWithJobs = COLUMNS.map((col, index) => ({
    ...col,
    jobs: jobList
      .filter((j) => columnIndexForStage(j.stage) === index)
      .sort((a, b) => (a.occurredAt < b.occurredAt ? 1 : -1))
      .slice(0, 8),
  }));

  return (
    <Card>
      <CardHeader
        title="Live Pipeline"
        subtitle="Real postings moving through discovery, screening, extraction, scoring, tailoring, and review - polled every 2.5s from the audit ledger, no fabricated activity"
      />
      {jobList.length === 0 ? (
        <p className="text-sm text-slate-400">
          {error
            ? `Couldn't load the live feed: ${error}`
            : "No pipeline activity yet - trigger a discovery poll or evaluate a match to see postings flow through here."}
        </p>
      ) : (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {columnsWithJobs.map((col) => (
            <div key={col.key} className="min-h-[140px] rounded-lg bg-slate-50 p-2">
              <p className="mb-2 text-center text-[11px] font-semibold uppercase tracking-wide text-slate-400">
                {col.label}
                {col.jobs.length > 0 && <span className="ml-1 text-slate-300">({col.jobs.length})</span>}
              </p>
              <div className="space-y-2">
                {col.jobs.map((job) => (
                  // Keying by stage (not just job id) forces a remount - and therefore
                  // the CSS entry animation below - every time a job advances a stage,
                  // even when it stays in the same column (e.g. EXTRACTED -> EMBEDDED).
                  <div
                    key={`${job.jobPostingId}-${job.stage}`}
                    className="pipeline-card-enter rounded-md border border-slate-200 bg-white p-2 shadow-sm"
                    title={job.title}
                  >
                    <p className="truncate text-xs font-medium text-slate-800">{job.title}</p>
                    <p className="truncate text-[11px] text-slate-400">{job.company}</p>
                    <span
                      className={`mt-1 inline-block rounded-full px-1.5 py-0.5 text-[10px] font-medium ring-1 ring-inset ${STAGE_TONE[job.stage]}`}
                    >
                      {job.stage}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
      <style>{`
        @keyframes pipelinePop {
          0% { opacity: 0; transform: translateY(6px) scale(0.96); }
          60% { opacity: 1; transform: translateY(-1px) scale(1.01); }
          100% { opacity: 1; transform: translateY(0) scale(1); }
        }
        .pipeline-card-enter { animation: pipelinePop 0.4s ease-out; }
      `}</style>
    </Card>
  );
}
