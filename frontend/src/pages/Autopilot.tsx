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
} from "lucide-react";
import { api } from "../api/client";
import type {
  AuditEvent,
  AutopilotStage,
  AutopilotStatusView,
  TailoringJob,
} from "../api/types";
import { useCandidate } from "../context/CandidateContext";
import { Card, CardHeader } from "../components/ui/Card";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { StatCard } from "../components/ui/StatCard";
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
  AUTOPILOT_APPLICATION_SUBMITTED: "Submitted an application",
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
  const [busy, setBusy] = useState(false);
  const [consoleTab, setConsoleTab] = useState<ConsoleTab>("logs");

  const load = () => {
    if (!selected) return;
    api.autopilot.status(selected.id).then(setView);
    api.autopilot.activity(selected.id).then(setActivity);
    api.tailoring.listJobsForCandidate(selected.id).then(setJobs);
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
  const recentApplications = jobs
    .filter((j) => j.resultArtifact?.status === "APPROVED")
    .slice(0, 5);

  return (
    <div className="space-y-6">
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
            {selected.fullName}&rsquo;s autonomous agent - discovers, scores,
            tailors and (per your settings) submits applications with no manual
            step. High-priority matches still route to your ordinary
            Applications review queue. Real submissions to live employer systems
            are not sent automatically - see Settings for the exact scope.
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
          label="Applications Submitted Today"
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
        <Card
          className="lg:col-span-2 !bg-[var(--color-sidebar)] !border-slate-800"
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

        <Card>
          <CardHeader
            title="Agent Settings"
            subtitle="Summary - edit on the Settings screen"
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
                  ? "Enabled (with review for high-priority roles)"
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
      </div>

      <Card>
        <CardHeader
          title="Recent Applications"
          subtitle="Approved by the agent (or auto-approved on your behalf)"
        />
        <div className="space-y-2">
          {recentApplications.map((j) => (
            <div
              key={j.id}
              className="flex items-center justify-between rounded-lg border border-slate-100 p-3 text-sm"
            >
              <div>
                <p className="font-medium text-slate-900">
                  {j.jobPosting.title} &middot; {j.jobPosting.company}
                </p>
                <p className="text-xs text-slate-400">
                  {j.resultArtifact?.reviewedAt &&
                    formatRelativeTime(j.resultArtifact.reviewedAt)}
                </p>
              </div>
              <Badge tone="green">Approved</Badge>
            </div>
          ))}
          {recentApplications.length === 0 && (
            <p className="text-sm text-slate-400">
              No agent-submitted applications yet.
            </p>
          )}
        </div>
      </Card>
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
