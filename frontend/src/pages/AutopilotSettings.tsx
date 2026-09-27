import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  ArrowLeft,
  Save,
  Search,
  Sparkles,
  Send,
  Radar,
  Bot,
  Pause,
  Compass,
  Wand2,
} from "lucide-react";
import { api } from "../api/client";
import type {
  AutopilotSettings as AutopilotSettingsType,
  AutopilotStatusView,
  ProfilePositioning,
} from "../api/types";
import { useCandidate } from "../context/CandidateContext";
import { Card, CardHeader } from "../components/ui/Card";
import { Badge, toneForScore } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { SkillChipInput } from "../components/ui/SkillChipInput";
import { StatCard } from "../components/ui/StatCard";
import { currencySymbol, fromMinorUnits, toMinorUnits, DEFAULT_CURRENCY } from "../lib/currency";

const JOB_SOURCES = [
  "GREENHOUSE",
  "LEVER",
  "LINKEDIN",
  "INDEED",
  "GLASSDOOR",
  "COMPANY_CAREER_PAGES",
];

// Same icon/label set as Autopilot.tsx's STAGES, condensed to the 4-step overview the
// mock's chat-intro panel shows (Search -> Match -> Apply -> Track).
const INTRO_STEPS = [
  {
    label: "Search Jobs",
    detail: "I'll search multiple sources",
    icon: Search,
  },
  {
    label: "Match & Rank",
    detail: "Score fit against your profile",
    icon: Sparkles,
  },
  { label: "Apply", detail: "Tailor & queue for your review", icon: Send },
  { label: "Track", detail: "You stay informed", icon: Radar },
];

function toChipString(values: string[]): string {
  return values.join(", ");
}

function fromChipString(value: string): string[] {
  return value
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
}

export function AutopilotSettingsPage() {
  const { selected } = useCandidate();
  const [settings, setSettings] = useState<AutopilotSettingsType | null>(null);
  const [statusView, setStatusView] = useState<AutopilotStatusView | null>(
    null,
  );
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [positioning, setPositioning] = useState<ProfilePositioning | null>(
    null,
  );
  const [positioningLoading, setPositioningLoading] = useState(false);

  // Chip-input fields are edited as comma-joined strings locally, same idiom as Onboarding.tsx
  const [roles, setRoles] = useState("");
  const [locations, setLocations] = useState("");
  const [keySkills, setKeySkills] = useState("");
  const [includeKeywords, setIncludeKeywords] = useState("");
  const [excludeKeywords, setExcludeKeywords] = useState("");
  const [trackedGreenhouseBoards, setTrackedGreenhouseBoards] = useState("");
  const [trackedLeverCompanies, setTrackedLeverCompanies] = useState("");

  useEffect(() => {
    if (!selected) return;
    api.autopilot.getSettings(selected.id).then((s) => {
      setSettings(s);
      setRoles(toChipString(s.preferredRoles));
      setLocations(toChipString(s.preferredLocations));
      setKeySkills(toChipString(s.keySkills));
      setIncludeKeywords(toChipString(s.includeKeywords));
      setExcludeKeywords(toChipString(s.excludeKeywords));
      setTrackedGreenhouseBoards(
        toChipString(
          s.trackedCompanySlugs
            .filter((slug) => slug.startsWith("greenhouse:"))
            .map((slug) => slug.slice("greenhouse:".length)),
        ),
      );
      setTrackedLeverCompanies(
        toChipString(
          s.trackedCompanySlugs
            .filter((slug) => slug.startsWith("lever:"))
            .map((slug) => slug.slice("lever:".length)),
        ),
      );
    });
  }, [selected]);

  // Right-rail "Agent Status" card - same status endpoint/polling idiom as Autopilot.tsx.
  useEffect(() => {
    if (!selected) return;
    api.autopilot.status(selected.id).then(setStatusView);
  }, [selected]);

  // Right-rail "Suggested Positioning" card - dynamically re-derived from the candidate's
  // Career Vault each time they land on this page (see backend ProfilePositioningService).
  useEffect(() => {
    if (!selected) return;
    setPositioningLoading(true);
    api.profilePositioning
      .get(selected.id)
      .then(setPositioning)
      .finally(() => setPositioningLoading(false));
  }, [selected]);

  useEffect(() => {
    if (!selected || statusView?.settings.status !== "RUNNING") return;
    const interval = setInterval(() => {
      api.autopilot.status(selected.id).then(setStatusView);
    }, 5000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected, statusView?.settings.status]);

  if (!selected) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Select a candidate first.</p>
      </Card>
    );
  }

  if (!settings) {
    return (
      <Card>
        <p className="text-sm text-slate-500">Loading agent settings...</p>
      </Card>
    );
  }

  const currency = selected.preferredCurrency || DEFAULT_CURRENCY;

  const update = <K extends keyof AutopilotSettingsType>(
    key: K,
    value: AutopilotSettingsType[K],
  ) => {
    setSettings({ ...settings, [key]: value });
    setSaved(false);
  };

  const applyPositioning = () => {
    if (!positioning) return;
    const suggestedRoles = positioning.suggestedTitles.map((t) => t.title);
    const mergedRoles = Array.from(
      new Set([...fromChipString(roles), ...suggestedRoles]),
    );
    setRoles(toChipString(mergedRoles));
    const mergedSkills = Array.from(
      new Set([...fromChipString(keySkills), ...positioning.suggestedKeywords]),
    );
    setKeySkills(toChipString(mergedSkills));
    setSaved(false);
  };

  const toggleSource = (source: string) => {
    const next = settings.enabledJobSources.includes(source)
      ? settings.enabledJobSources.filter((s) => s !== source)
      : [...settings.enabledJobSources, source];
    update("enabledJobSources", next);
  };

  const save = async () => {
    setSaving(true);
    try {
      const updated = await api.autopilot.updateSettings(selected.id, {
        preferredRoles: fromChipString(roles),
        preferredLocations: fromChipString(locations),
        experienceLevel: settings.experienceLevel,
        jobType: settings.jobType,
        salaryMinMinorUnits: settings.salaryMinMinorUnits,
        salaryMaxMinorUnits: settings.salaryMaxMinorUnits,
        remoteOnly: settings.remoteOnly,
        openToRelocation: settings.openToRelocation,
        includeGlobalOpportunities: settings.includeGlobalOpportunities,
        keySkills: fromChipString(keySkills),
        includeKeywords: fromChipString(includeKeywords),
        excludeKeywords: fromChipString(excludeKeywords),
        enabledJobSources: settings.enabledJobSources,
        trackedCompanySlugs: [
          ...fromChipString(trackedGreenhouseBoards).map((s) => `greenhouse:${s}`),
          ...fromChipString(trackedLeverCompanies).map((s) => `lever:${s}`),
        ],
        autoApply: settings.autoApply,
        aiTailorResume: settings.aiTailorResume,
        generateCoverLetter: settings.generateCoverLetter,
        autoFillForms: settings.autoFillForms,
        skipSponsorshipRequired: settings.skipSponsorshipRequired,
        notifyBeforeApplying: settings.notifyBeforeApplying,
        autoFollowUp: settings.autoFollowUp,
        dailyApplicationLimit: settings.dailyApplicationLimit,
        notifyNewMatches: settings.notifyNewMatches,
        notifyApplicationSubmitted: settings.notifyApplicationSubmitted,
        notifyStatusChanges: settings.notifyStatusChanges,
        notifyInterviewInvitations: settings.notifyInterviewInvitations,
        notifyWeeklySummary: settings.notifyWeeklySummary,
      });
      setSettings(updated);
      setSaved(true);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <Link
            to="/autopilot"
            className="mb-1 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-700"
          >
            <ArrowLeft size={14} /> Back to Agent
          </Link>
          <h1 className="text-2xl font-bold text-slate-900">
            AI Job Agent Settings
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Criteria and behavior for {selected.fullName}&rsquo;s autopilot
            agent.
          </p>
        </div>
        <Button onClick={save} disabled={saving} icon={<Save size={16} />}>
          {saving ? "Saving..." : saved ? "Saved" : "Save changes"}
        </Button>
      </div>

      <Card className="bg-gradient-to-br from-blue-50 via-white to-teal-50">
        <div className="flex flex-wrap items-start gap-4 sm:flex-nowrap">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-[var(--color-brand-from)] to-[var(--color-brand-to)] text-white shadow-sm">
            <Bot size={22} />
          </div>
          <div className="flex-1 rounded-2xl rounded-tl-sm bg-white/80 p-4 shadow-sm ring-1 ring-inset ring-slate-100">
            <p className="text-sm font-semibold text-slate-900">
              Hi {selected.fullName.split(" ")[0]}!
            </p>
            <p className="mt-0.5 text-sm text-slate-600">
              I&rsquo;ll search, match, apply and track jobs for you based on
              your preferences. You can customize everything here.
            </p>
          </div>
        </div>
        <div className="mt-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {INTRO_STEPS.map((step, i) => {
            const Icon = step.icon;
            return (
              <div
                key={step.label}
                className="flex flex-col items-center gap-1.5 text-center"
              >
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white text-blue-600 shadow-sm ring-1 ring-inset ring-slate-100">
                  <Icon size={17} />
                </div>
                <p className="text-xs font-semibold text-slate-800">
                  {i + 1}. {step.label}
                </p>
                <p className="text-[11px] text-slate-500">{step.detail}</p>
              </div>
            );
          })}
        </div>
      </Card>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader title="Job Preferences" />
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <Field label="Preferred Roles">
                <SkillChipInput
                  value={roles}
                  onChange={setRoles}
                  placeholder="e.g. Backend Engineer - press Enter"
                />
              </Field>
              <Field label="Preferred Locations">
                <SkillChipInput
                  value={locations}
                  onChange={setLocations}
                  placeholder="e.g. Remote, Bengaluru - press Enter"
                />
              </Field>
              <Field label="Experience Level">
                <Select
                  value={settings.experienceLevel}
                  onChange={(v) => update("experienceLevel", v)}
                  options={["ENTRY", "MID", "SENIOR", "STAFF_PLUS"]}
                />
              </Field>
              <Field label="Job Type">
                <Select
                  value={settings.jobType}
                  onChange={(v) => update("jobType", v)}
                  options={["FULL_TIME", "CONTRACT", "INTERNSHIP", "PART_TIME"]}
                />
              </Field>
              <Field label={`Minimum Salary (annual, ${currencySymbol(currency)})`}>
                <input
                  type="number"
                  className={inputClass}
                  value={
                    settings.salaryMinMinorUnits
                      ? fromMinorUnits(settings.salaryMinMinorUnits, currency)
                      : ""
                  }
                  onChange={(e) =>
                    update(
                      "salaryMinMinorUnits",
                      e.target.value ? toMinorUnits(Number(e.target.value), currency) : null,
                    )
                  }
                />
              </Field>
              <Field label={`Maximum Salary (annual, ${currencySymbol(currency)})`}>
                <input
                  type="number"
                  className={inputClass}
                  value={
                    settings.salaryMaxMinorUnits
                      ? fromMinorUnits(settings.salaryMaxMinorUnits, currency)
                      : ""
                  }
                  onChange={(e) =>
                    update(
                      "salaryMaxMinorUnits",
                      e.target.value ? toMinorUnits(Number(e.target.value), currency) : null,
                    )
                  }
                />
              </Field>
            </div>
            <div className="mt-4 flex flex-wrap gap-5">
              <Checkbox
                label="Remote only"
                checked={settings.remoteOnly}
                onChange={(v) => update("remoteOnly", v)}
              />
              <Checkbox
                label="Open to relocation"
                checked={settings.openToRelocation}
                onChange={(v) => update("openToRelocation", v)}
              />
              <Checkbox
                label="Include global opportunities"
                checked={settings.includeGlobalOpportunities}
                onChange={(v) => update("includeGlobalOpportunities", v)}
              />
            </div>
          </Card>

          <Card>
            <CardHeader title="Skills & Keywords" />
            <div className="space-y-4">
              <Field label="Key Skills">
                <SkillChipInput
                  value={keySkills}
                  onChange={setKeySkills}
                  placeholder="e.g. Java, Spring, AWS - press Enter"
                />
              </Field>
              <Field label="Keywords to include (optional)">
                <SkillChipInput
                  value={includeKeywords}
                  onChange={setIncludeKeywords}
                  placeholder="e.g. platform, distributed systems"
                />
              </Field>
              <Field
                label="Exclude keywords"
                hint="Postings mentioning any of these are skipped entirely - the closest thing to a blacklist."
              >
                <SkillChipInput
                  value={excludeKeywords}
                  onChange={setExcludeKeywords}
                  placeholder="e.g. internship, unpaid, onsite-only"
                />
              </Field>
            </div>
          </Card>

          <Card>
            <CardHeader
              title="Job Sources"
              subtitle="Which discovery adapters the agent scans"
            />
            <div className="flex flex-wrap gap-2">
              {JOB_SOURCES.map((source) => (
                <button
                  key={source}
                  type="button"
                  onClick={() => toggleSource(source)}
                  className={`rounded-full px-3 py-1.5 text-xs font-medium ring-1 ring-inset transition-colors ${
                    settings.enabledJobSources.includes(source)
                      ? "bg-blue-50 text-blue-700 ring-blue-600/30"
                      : "bg-slate-50 text-slate-500 ring-slate-200"
                  }`}
                >
                  {source.replace(/_/g, " ")}
                </button>
              ))}
            </div>
            <p className="mt-2 text-xs text-slate-400">
              GREENHOUSE and LEVER are the only adapters this backend actually
              polls (see docs/04 FR-1); the rest are placeholders matching the
              mock's source list.
            </p>
            <div className="mt-4 space-y-4 border-t border-slate-100 pt-4">
              <Field
                label="Tracked Greenhouse boards"
                hint="Board tokens from a company's careers URL (e.g. gitlab). Discovery polls these alongside the app's default boards whenever this agent is running."
              >
                <SkillChipInput
                  value={trackedGreenhouseBoards}
                  onChange={setTrackedGreenhouseBoards}
                  placeholder="e.g. gitlab, notion - press Enter"
                />
              </Field>
              <Field
                label="Tracked Lever companies"
                hint="Company slugs from a Lever careers URL (e.g. palantir)."
              >
                <SkillChipInput
                  value={trackedLeverCompanies}
                  onChange={setTrackedLeverCompanies}
                  placeholder="e.g. palantir, ramp - press Enter"
                />
              </Field>
            </div>
          </Card>

          <Card>
            <CardHeader title="Application Settings" />
            <div className="space-y-3">
              <Checkbox
                label="Auto-tailor and queue best matches for review"
                checked={settings.autoApply}
                onChange={(v) => update("autoApply", v)}
              />
              <Checkbox
                label="Use AI to tailor resume & cover letter"
                checked={settings.aiTailorResume}
                onChange={(v) => update("aiTailorResume", v)}
              />
              <Checkbox
                label="Generate customized cover letters"
                checked={settings.generateCoverLetter}
                onChange={(v) => update("generateCoverLetter", v)}
              />
              <Checkbox
                label="Fill application forms automatically"
                checked={settings.autoFillForms}
                onChange={(v) => update("autoFillForms", v)}
              />
              <Checkbox
                label="Skip jobs requiring sponsorship"
                checked={settings.skipSponsorshipRequired}
                onChange={(v) => update("skipSponsorshipRequired", v)}
              />
              <Checkbox
                label="Notify me before applying"
                checked={settings.notifyBeforeApplying}
                onChange={(v) => update("notifyBeforeApplying", v)}
              />
              <Checkbox
                label="Auto follow-up after 7 days"
                checked={settings.autoFollowUp}
                onChange={(v) => update("autoFollowUp", v)}
              />
              <Field label="Daily application limit">
                <Select
                  value={String(settings.dailyApplicationLimit)}
                  onChange={(v) => update("dailyApplicationLimit", Number(v))}
                  options={["5", "10", "15", "20", "30", "50"]}
                  labelFn={(v) => `${v} applications`}
                />
              </Field>
            </div>
          </Card>

          <Card>
            <CardHeader title="Notifications" />
            <div className="space-y-3">
              <Checkbox
                label="New job matches"
                checked={settings.notifyNewMatches}
                onChange={(v) => update("notifyNewMatches", v)}
              />
              <Checkbox
                label="Application submitted"
                checked={settings.notifyApplicationSubmitted}
                onChange={(v) => update("notifyApplicationSubmitted", v)}
              />
              <Checkbox
                label="Application status changes"
                checked={settings.notifyStatusChanges}
                onChange={(v) => update("notifyStatusChanges", v)}
              />
              <Checkbox
                label="Interview invitations"
                checked={settings.notifyInterviewInvitations}
                onChange={(v) => update("notifyInterviewInvitations", v)}
              />
              <Checkbox
                label="Weekly summary report"
                checked={settings.notifyWeeklySummary}
                onChange={(v) => update("notifyWeeklySummary", v)}
              />
            </div>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader
              title="Suggested Positioning"
              subtitle="Dynamically derived from your Career Vault"
              action={<Compass size={16} className="text-slate-400" />}
            />
            {positioningLoading && !positioning ? (
              <p className="text-sm text-slate-400">
                Reasoning about your profile...
              </p>
            ) : !positioning || !positioning.hasEnoughData ? (
              <p className="text-sm text-slate-500">
                {positioning?.rationale ??
                  "Add experience, achievements, or skills to your Career Vault to get positioning suggestions."}
              </p>
            ) : (
              <>
                {positioning.suggestedSeniority && (
                  <div className="mb-3 flex items-center gap-2">
                    <span className="text-xs font-medium text-slate-500">
                      Seniority read:
                    </span>
                    <Badge tone={toneForScore(positioning.seniorityConfidence)}>
                      {positioning.suggestedSeniority}
                    </Badge>
                  </div>
                )}
                {positioning.suggestedTitles.length > 0 && (
                  <div className="mb-3">
                    <p className="mb-1.5 text-xs font-medium text-slate-500">
                      Target titles
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {positioning.suggestedTitles.map((t) => (
                        <Badge key={t.title} tone={toneForScore(t.confidence)}>
                          {t.title}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
                {positioning.suggestedKeywords.length > 0 && (
                  <div className="mb-3">
                    <p className="mb-1.5 text-xs font-medium text-slate-500">
                      Keywords, from your skills
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {positioning.suggestedKeywords.map((k) => (
                        <Badge key={k} tone="slate">
                          {k}
                        </Badge>
                      ))}
                    </div>
                  </div>
                )}
                <p className="mb-3 text-xs leading-relaxed text-slate-500">
                  {positioning.rationale}
                </p>
                <Button
                  variant="secondary"
                  className="w-full"
                  icon={<Wand2 size={14} />}
                  onClick={applyPositioning}
                  disabled={
                    positioning.suggestedTitles.length === 0 &&
                    positioning.suggestedKeywords.length === 0
                  }
                >
                  Apply to Job Preferences
                </Button>
              </>
            )}
          </Card>

          <Card>
            <CardHeader
              title="Agent Status"
              action={
                statusView && (
                  <Badge
                    tone={
                      statusView.settings.status === "RUNNING"
                        ? "green"
                        : statusView.settings.status === "PAUSED"
                          ? "amber"
                          : "slate"
                    }
                  >
                    {statusView.settings.status === "RUNNING"
                      ? "Active"
                      : statusView.settings.status}
                  </Badge>
                )
              }
            />
            {statusView ? (
              <>
                <p className="text-sm text-slate-500">
                  {statusView.settings.status === "RUNNING"
                    ? "Searching, matching and applying for new opportunities."
                    : statusView.settings.status === "PAUSED"
                      ? "Paused - resume from the Agent screen to continue."
                      : "Stopped - start the agent from the Agent screen."}
                </p>
                <div className="mt-4 grid grid-cols-2 gap-3">
                  <StatCard
                    icon={<Search size={16} />}
                    value={statusView.stats.jobsScannedToday}
                    label="Jobs Scanned"
                  />
                  <StatCard
                    icon={<Send size={16} />}
                    iconTone="green"
                    value={statusView.stats.applicationsSubmittedToday}
                    label="Queued Today"
                  />
                  <StatCard
                    icon={<Sparkles size={16} />}
                    iconTone="purple"
                    value={statusView.stats.goodMatchesFound}
                    label="Good Matches"
                  />
                  <StatCard
                    icon={<Radar size={16} />}
                    iconTone="amber"
                    value={statusView.stats.interviewsScheduled}
                    label="Interviews"
                  />
                </div>
                <Link to="/autopilot">
                  <Button
                    variant="secondary"
                    className="mt-4 w-full"
                    icon={<Pause size={14} />}
                  >
                    Manage on Agent screen
                  </Button>
                </Link>
              </>
            ) : (
              <p className="text-sm text-slate-400">Loading agent status...</p>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
}

const inputClass =
  "w-full rounded-lg border border-slate-200 px-3 py-2 text-sm focus:border-blue-400 focus:outline-none";

function Field({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium text-slate-700">
        {label}
      </span>
      {children}
      {hint && (
        <span className="mt-1 block text-xs text-slate-400">{hint}</span>
      )}
    </label>
  );
}

function Select({
  value,
  onChange,
  options,
  labelFn,
}: {
  value: string;
  onChange: (v: string) => void;
  options: string[];
  labelFn?: (v: string) => string;
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={inputClass}
    >
      {options.map((o) => (
        <option key={o} value={o}>
          {labelFn ? labelFn(o) : o.replace(/_/g, " ")}
        </option>
      ))}
    </select>
  );
}

function Checkbox({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label className="flex items-center gap-2 text-sm text-slate-700">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="h-4 w-4 rounded border-slate-300"
      />
      {label}
    </label>
  );
}
