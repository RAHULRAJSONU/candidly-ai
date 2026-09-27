import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  Bot,
  Pause,
  Play,
  Search,
  Sparkles,
  FileEdit,
  Send,
  Radar,
  MessageCircle,
  Settings,
  Square,
  Terminal,
  Globe,
  Camera,
  CheckCircle2,
  Clock,
} from "lucide-react";
import { api } from "../api/client";
import type {
  AuditEvent,
  AutopilotStage,
  AutopilotStatusView,
  MatchScorecard,
  TailoringJob,
} from "../api/types";
import { useCandidate } from "../context/CandidateContext";
import { Card, CardHeader } from "../components/ui/Card";
import { Badge, toneForScore } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { StatCard } from "../components/ui/StatCard";
import { CompanyAvatar } from "../components/ui/CompanyAvatar";
import { ErrorBanner, describeError } from "../components/ui/ErrorBanner";
import { formatRelativeTime } from "../lib/format";

const STAGES: { key: AutopilotStage; label: string; icon: typeof Search }[] = [
  { key: "SEARCH_JOBS", label: "Search Jobs", icon: Search },
  { key: "MATCH_AND_RANK", label: "Match & Rank", icon: Sparkles },
  { key: "CUSTOMIZE", label: "Customize", icon: FileEdit },
  { key: "APPLY", label: "Apply", icon: Send },
  { key: "TRACK", label: "Track", icon: Radar },
  { key: "FOLLOW_UP", label: "Follow Up", icon: MessageCircle },
];

const ACTIVITY_LABEL: Record<string, string> = {
  AUTOPILOT_CYCLE_STARTED: "Agent cycle started",
  MATCH_SCORED: "Scored a job posting match",
  TAILORED_ARTIFACT_GENERATED: "Generated a tailored resume",
  AUTOPILOT_APPLICATION_SUBMITTED: "Queued an application for your review",
  TAILORED_ARTIFACT_REVIEWED: "Reviewed a tailored artifact",
  AUTOPILOT_FOLLOW_UP_LOGGED:
    "Logged a follow-up (no email sent automatically)",
  AUTOPILOT_CYCLE_COMPLETED: "Agent cycle completed",
};

/** Log-line color tinting per event severity, for the terminal-style console below -
 * a display-only banding, not a new status taxonomy. */
const ACTIVITY_TONE: Record<string, string> = {
  AUTOPILOT_CYCLE_STARTED: "text-sky-400",
  MATCH_SCORED: "text-sky-400",
  TAILORED_ARTIFACT_GENERATED: "text-amber-400",
  AUTOPILOT_APPLICATION_SUBMITTED: "text-emerald-400",
  TAILORED_ARTIFACT_REVIEWED: "text-emerald-400",
  AUTOPILOT_FOLLOW_UP_LOGGED: "text-violet-400",
  AUTOPILOT_CYCLE_COMPLETED: "text-emerald-400",
};

/** Same event types, mapped to an icon + a light-background chip color for the "Agent
 * Activity (Live)" list - the mock's colored-icon-per-row treatment. */
const ACTIVITY_ICON: Record<string, { icon: typeof Search; className: string }> = {
  AUTOPILOT_CYCLE_STARTED: { icon: Bot, className: "bg-slate-100 text-slate-600" },
  MATCH_SCORED: { icon: Search, className: "bg-blue-100 text-blue-600" },
  TAILORED_ARTIFACT_GENERATED: { icon: FileEdit, className: "bg-amber-100 text-amber-600" },
  AUTOPILOT_APPLICATION_SUBMITTED: { icon: Send, className: "bg-emerald-100 text-emerald-600" },
  TAILORED_ARTIFACT_REVIEWED: { icon: CheckCircle2, className: "bg-emerald-100 text-emerald-600" },
  AUTOPILOT_FOLLOW_UP_LOGGED: { icon: MessageCircle, className: "bg-violet-100 text-violet-600" },
  AUTOPILOT_CYCLE_COMPLETED: { icon: CheckCircle2, className: "bg-emerald-100 text-emerald-600" },
};

function consoleTimestamp(iso: string): string {
  return new Date(iso).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

type ConsoleTab = "logs" | "browser" | "screenshots";

export function Autopilot() {
  const { selected } = useCandidate();
  const [view, setView] = useState<AutopilotStatusView | null>(null);
  const [activity, setActivity] = useState<AuditEvent[]>([]);
  const [jobs, setJobs] = useState<TailoringJob[]>([]);
  const [matches, setMatches] = useState<MatchScorecard[]>([]);
  const [busy, setBusy] = useState(false);
  const [consoleTab, setConsoleTab] = useState<ConsoleTab>("logs");
  const [loadError, setLoadError] = useState<string | null>(null);

  const load = () => {
    if (!selected) return;
    setLoadError(null);
    api.autopilot.status(selected.id).then(setView).catch((e) => setLoadError(describeError(e)));
    api.autopilot.activity(selected.id).then(setActivity).catch((e) => setLoadError(describeError(e)));
    api.tailoring.listJobsForCandidate(selected.id).then(setJobs).catch((e) => setLoadError(describeError(e)));
    api.matches.listForCandidate(selected.id).then(setMatches).catch((e) => setLoadError(describeError(e)));
  };

  useEffect(load, [selected]);

  useEffect(() => {
    if (!selected || view?.settings.status !== "RUNNING") return;
    const interval = setInterval(load, 5000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected, view?.settings.status]);

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    );
  }

  if (!view) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Loading agent status...</p>
      </Card>
    );
  }

  const { settings, stats } = view;

  const run = async (action: "start" | "pause" | "stop" | "runNow") => {
    setBusy(true);
    try {
      await api.autopilot[action](selected.id);
      load();
    } finally {
      setBusy(false);
    }
  };

  const statusTone =
    settings.status === "RUNNING"
      ? "green"
      : settings.status === "PAUSED"
        ? "amber"
        : "slate";
  const scoreByJobPostingId = new Map(matches.map((m) => [m.jobPosting.id, m.compositeScore]));
  const recentApplications = jobs
    .filter((j) => j.resultArtifact?.status === "APPROVED")
    .sort(
      (a, b) =>
        new Date(b.resultArtifact!.reviewedAt ?? b.completedAt ?? b.submittedAt).getTime() -
        new Date(a.resultArtifact!.reviewedAt ?? a.completedAt ?? a.submittedAt).getTime(),
    )
    .slice(0, 5);

  const followedUpJobIds = new Set(
    activity
      .filter((e) => e.eventType === "AUTOPILOT_FOLLOW_UP_LOGGED")
      .map((e) => e.details?.match(/^job=([0-9a-f-]+)/)?.[1])
      .filter((id): id is string => Boolean(id)),
  );
  const upcomingFollowUps = jobs
    .filter((j) => j.resultArtifact?.status === "APPROVED" && j.resultArtifact.reviewedAt)
    .filter((j) => !followedUpJobIds.has(j.jobPosting.id))
    .map((j) => {
      const reviewedAt = new Date(j.resultArtifact!.reviewedAt!);
      const followUpAt = new Date(reviewedAt.getTime() + 7 * 24 * 60 * 60 * 1000);
      return { job: j, followUpAt };
    })
    .sort((a, b) => a.followUpAt.getTime() - b.followUpAt.getTime())
    .slice(0, 5);

  return (
    <div className="space-y-6">
      {loadError && <ErrorBanner message={`Couldn't load autopilot data: ${loadError}`} />}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-[var(--color-brand-from)] to-[var(--color-brand-to)] text-white">
              <Bot size={18} />
            </div>
            <h1 className="text-2xl font-bold text-slate-900">
              AI Job Application Agent
            </h1>
            <Badge tone={statusTone}>{settings.status}</Badge>
          </div>
          <p className="mt-1 text-sm text-slate-500">
            {selected.fullName}&rsquo;s agent - discovers, scores and tailors
            applications with no manual step, then queues every result in your
            ordinary Applications review queue. Nothing is ever submitted
            without you approving it there - see Settings for the exact scope.
          </p>
          {settings.nextRunAt && settings.status === "RUNNING" && (
            <p className="mt-1 text-xs text-slate-400">
              Next run: {new Date(settings.nextRunAt).toLocaleString()}
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-2">
          <Link to="/autopilot/settings">
            <Button variant="secondary" icon={<Settings size={16} />}>
              Agent Settings
            </Button>
          </Link>
          {settings.status !== "RUNNING" ? (
            <Button
              onClick={() => run("start")}
              disabled={busy}
              icon={<Play size={16} />}
            >
              {settings.status === "PAUSED"
                ? "Resume Agent"
                : "Enable Autopilot"}
            </Button>
          ) : (
            <Button
              variant="secondary"
              onClick={() => run("pause")}
              disabled={busy}
              icon={<Pause size={16} />}
            >
              Pause Agent
            </Button>
          )}
          {settings.status !== "STOPPED" && (
            <Button
              variant="danger"
              onClick={() => run("stop")}
              disabled={busy}
              icon={<Square size={16} />}
            >
              Stop Agent
            </Button>
          )}
          <Button
            variant="secondary"
            onClick={() => run("runNow")}
            disabled={busy}
          >
            Run cycle now
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader
          title="Agent pipeline"
          subtitle={settings.currentStageDetail}
        />
        <div className="flex flex-wrap gap-3">
          {STAGES.map((stage, i) => {
            const Icon = stage.icon;
            const active = settings.currentStage === stage.key;
            const done =
              STAGES.findIndex((s) => s.key === settings.currentStage) > i &&
              settings.status !== "STOPPED";
            return (
              <div key={stage.key} className="flex flex-1 items-center gap-2">
                <div
                  className={`flex min-w-[110px] flex-1 flex-col items-center gap-2 rounded-xl p-3 text-center transition-colors ${
                    active
                      ? "bg-blue-50 text-blue-700 ring-1 ring-inset ring-blue-600/30"
                      : done
                        ? "bg-emerald-50 text-emerald-700"
                        : "bg-slate-50 text-slate-400"
                  }`}
                >
                  <div
                    className={`flex h-9 w-9 items-center justify-center rounded-full ${
                      active
                        ? "bg-gradient-to-br from-[var(--color-brand-from)] to-[var(--color-brand-to)] text-white shadow-sm"
                        : done
                          ? "bg-emerald-100 text-emerald-600"
                          : "bg-slate-200/70 text-slate-400"
                    }`}
                  >
                    <Icon size={16} />
                  </div>
                  <p className="text-xs font-medium">{stage.label}</p>
                </div>
                {i < STAGES.length - 1 && (
                  <span className="hidden shrink-0 text-slate-300 sm:inline">
                    &rarr;
                  </span>
                )}
              </div>
            );
          })}
        </div>
      </Card>

      <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
        <StatCard
          icon={<Search size={18} />}
          value={stats.jobsScannedToday}
          label="Jobs Scanned Today"
        />
        <StatCard
          icon={<Send size={18} />}
          iconTone="green"
          value={stats.applicationsSubmittedToday}
          label="Queued for Review Today"
        />
        <StatCard
          icon={<Sparkles size={18} />}
          iconTone="purple"
          value={stats.goodMatchesFound}
          label="Good Matches Found"
        />
        <StatCard
          icon={<Bot size={18} />}
          iconTone="amber"
          value={stats.companiesTargeted}
          label="Companies Targeted"
        />
        <StatCard
          icon={<MessageCircle size={18} />}
          value={stats.interviewsScheduled}
          label="Interviews Scheduled"
        />
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card padded={false}>
          <div className="flex items-center justify-between gap-2 border-b border-slate-100 px-5 pt-5 pb-3">
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-semibold text-slate-900">
                  Agent Activity
                </h3>
                <span className="text-xs font-medium text-slate-400">
                  (Live)
                </span>
              </div>
              <p className="mt-0.5 text-sm text-slate-500">
                What the agent has done, most recent first
              </p>
            </div>
          </div>
          <div className="max-h-96 space-y-3 overflow-y-auto px-5 py-4">
            {activity.map((e) => {
              const meta = ACTIVITY_ICON[e.eventType] ?? {
                icon: Bot,
                className: "bg-slate-100 text-slate-500",
              };
              const Icon = meta.icon;
              return (
                <div key={e.id} className="flex items-start gap-3">
                  <div
                    className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${meta.className}`}
                  >
                    <Icon size={13} />
                  </div>
                  <div className="min-w-0">
                    <p className="text-sm text-slate-700">
                      {ACTIVITY_LABEL[e.eventType] ?? e.eventType}
                    </p>
                    <p className="text-xs text-slate-400">
                      {formatRelativeTime(e.occurredAt)}
                    </p>
                  </div>
                </div>
              );
            })}
            {activity.length === 0 && (
              <p className="text-sm text-slate-400">
                No agent activity yet - start the agent to begin.
              </p>
            )}
          </div>
        </Card>

        <Card
          className="!bg-[var(--color-sidebar)] !border-slate-800"
          padded={false}
        >
          <div className="flex items-center justify-between gap-4 border-b border-slate-800 px-5 pt-5 pb-3">
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-semibold text-white">
                  Agent Console
                </h3>
                <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-400">
                  <span className="relative flex h-2 w-2">
                    <span className="absolute inline-flex h-full w-full animate-pulse rounded-full bg-emerald-400" />
                  </span>
                  Live
                </span>
              </div>
              <p className="mt-0.5 text-sm text-slate-400">
                Real-time agent activity log
              </p>
            </div>
            <div className="flex items-center gap-1 rounded-lg bg-slate-900/60 p-1 text-xs font-medium">
              <button
                type="button"
                onClick={() => setConsoleTab("logs")}
                className={`flex items-center gap-1.5 rounded-md px-2.5 py-1.5 transition-colors ${
                  consoleTab === "logs"
                    ? "bg-slate-700 text-white"
                    : "text-slate-400 hover:text-slate-200"
                }`}
              >
                <Terminal size={13} /> Logs
              </button>
              <button
                type="button"
                disabled
                title="Not available - no autonomous browser session exists"
                className="flex cursor-not-allowed items-center gap-1.5 rounded-md px-2.5 py-1.5 text-slate-600"
              >
                <Globe size={13} /> Browser
              </button>
              <button
                type="button"
                disabled
                title="Not available - no autonomous browser session exists"
                className="flex cursor-not-allowed items-center gap-1.5 rounded-md px-2.5 py-1.5 text-slate-600"
              >
                <Camera size={13} /> Screenshots
              </button>
            </div>
          </div>
          <div className="max-h-96 space-y-1.5 overflow-y-auto px-5 py-4 font-mono text-xs">
            {activity.map((e) => (
              <div
                key={e.id}
                className="flex items-start gap-2 leading-relaxed"
              >
                <span className="shrink-0 text-slate-500">
                  [{consoleTimestamp(e.occurredAt)}]
                </span>
                <span
                  className={`shrink-0 ${ACTIVITY_TONE[e.eventType] ?? "text-slate-300"}`}
                >
                  {ACTIVITY_LABEL[e.eventType] ?? e.eventType}
                </span>
                {e.details && (
                  <span className="truncate text-slate-500">- {e.details}</span>
                )}
              </div>
            ))}
            {activity.length === 0 && (
              <p className="text-slate-500">
                No agent activity yet - start the agent to begin.
              </p>
            )}
            <div className="flex items-center gap-1 pt-1 text-slate-500">
              <span>&gt;</span>
              <span className="h-3.5 w-1.5 animate-pulse bg-slate-500" />
            </div>
          </div>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader
              title="Agent Settings"
              subtitle="Summary - edit on the Settings screen"
              action={
                <Link
                  to="/autopilot/settings"
                  className="text-xs font-medium text-blue-600 hover:text-blue-700"
                >
                  Edit
                </Link>
              }
            />
            <dl className="space-y-2 text-sm">
              <Row
                label="Target Roles"
                value={settings.preferredRoles.join(", ") || "Any"}
              />
              <Row
                label="Locations"
                value={settings.preferredLocations.join(", ") || "Any"}
              />
              <Row label="Experience Level" value={settings.experienceLevel} />
              <Row
                label="Job Sources"
                value={settings.enabledJobSources.join(", ") || "None enabled"}
              />
              <Row
                label="Auto Apply"
                value={
                  settings.autoApply
                    ? "Enabled (auto-tailors and queues for your review)"
                    : "Disabled"
                }
              />
              <Row
                label="Follow Up"
                value={
                  settings.autoFollowUp
                    ? "Auto follow-up after 7 days"
                    : "Disabled"
                }
              />
              <Row
                label="Daily limit"
                value={`${settings.dailyApplicationLimit} applications`}
              />
            </dl>
          </Card>

          <Card className="bg-gradient-to-br from-blue-50 via-white to-teal-50">
            <div className="flex items-center gap-2">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-gradient-to-br from-[var(--color-brand-from)] to-[var(--color-brand-to)] text-white">
                <Bot size={16} />
              </div>
              <h3 className="text-sm font-semibold text-slate-900">
                Your AI Agent Works 24/7
              </h3>
            </div>
            <p className="mt-2 text-sm text-slate-600">
              Finding opportunities, tailoring applications, and tracking
              status - so you can focus on what matters most. Every submission
              still waits for your approval in the Applications queue.
            </p>
          </Card>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <Card padded={false}>
          <div className="px-5 pt-5">
            <CardHeader
              title="Recent Applications"
              subtitle="Approved by you on the Applications review queue"
            />
          </div>
          <div className="overflow-x-auto px-5 pb-5">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="text-xs text-slate-400">
                  <th className="pb-2 font-medium">Company</th>
                  <th className="pb-2 font-medium">Role</th>
                  <th className="pb-2 font-medium">Match</th>
                  <th className="pb-2 font-medium">Status</th>
                  <th className="pb-2 font-medium">Applied On</th>
                </tr>
              </thead>
              <tbody>
                {recentApplications.map((j) => {
                  const score = scoreByJobPostingId.get(j.jobPosting.id);
                  const appliedOn =
                    j.resultArtifact?.reviewedAt ??
                    j.completedAt ??
                    j.submittedAt;
                  return (
                    <tr key={j.id} className="border-t border-slate-100">
                      <td className="py-2.5 pr-2">
                        <div className="flex items-center gap-2">
                          <CompanyAvatar name={j.jobPosting.company} size={24} />
                          <span className="font-medium text-slate-800">
                            {j.jobPosting.company}
                          </span>
                        </div>
                      </td>
                      <td className="py-2.5 pr-2 text-slate-600">
                        {j.jobPosting.title}
                      </td>
                      <td className="py-2.5 pr-2">
                        {score != null && (
                          <Badge tone={toneForScore(score)}>
                            {Math.round(score * 100)}%
                          </Badge>
                        )}
                      </td>
                      <td className="py-2.5 pr-2">
                        <Badge tone="green">Approved</Badge>
                      </td>
                      <td className="py-2.5 text-slate-500">
                        {new Date(appliedOn).toLocaleDateString()}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            {recentApplications.length === 0 && (
              <p className="py-2 text-sm text-slate-400">
                No approved applications yet.
              </p>
            )}
          </div>
        </Card>

        <Card padded={false}>
          <div className="px-5 pt-5">
            <CardHeader
              title="Upcoming Follow-ups"
              subtitle="7 days after approval, if there's no interview yet"
            />
          </div>
          <div className="space-y-2 px-5 pb-5">
            {upcomingFollowUps.map(({ job, followUpAt }) => {
              const daysUntil = Math.ceil(
                (followUpAt.getTime() - Date.now()) / (24 * 60 * 60 * 1000),
              );
              return (
                <div
                  key={job.id}
                  className="flex items-center justify-between rounded-lg border border-slate-100 p-3 text-sm"
                >
                  <div className="flex items-center gap-2">
                    <CompanyAvatar name={job.jobPosting.company} size={28} />
                    <div>
                      <p className="font-medium text-slate-800">
                        {job.jobPosting.company}
                      </p>
                      <p className="text-xs text-slate-400">
                        {job.jobPosting.title}
                      </p>
                    </div>
                  </div>
                  <span className="flex items-center gap-1 text-xs font-medium text-slate-500">
                    <Clock size={12} />
                    {daysUntil <= 0
                      ? "Due now"
                      : `In ${daysUntil} day${daysUntil === 1 ? "" : "s"}`}
                  </span>
                </div>
              );
            })}
            {upcomingFollowUps.length === 0 && (
              <p className="text-sm text-slate-400">
                No follow-ups pending.
              </p>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-right font-medium text-slate-800">{value}</dd>
    </div>
  );
}
