export interface CandidateExperienceInput {
  employer: string
  title: string
  startDate: string
  endDate: string | null
  narrative: string
  verifiedMetrics: string[]
  rawSkillMentions: string[]
}

export interface CandidateRequest {
  fullName: string
  email: string
  location: string
  workAuthorizations: string[]
  compFloorMinorUnits: number
  rawSkillMentions: string[]
  experiences: CandidateExperienceInput[]
}

export interface Candidate {
  id: string
  fullName: string
  email: string
  location: string
  workAuthorizations: string[]
  compFloorMinorUnits: number
  skillIds: string[]
  rawSkillMentions: string[]
}

// --- Resume / LinkedIn profile import (onboarding pre-fill) ---

export interface ProfileImportExperienceDraft {
  employer: string | null
  title: string | null
  startDate: string | null
  endDate: string | null
  narrative: string | null
  rawSkillMentions: string[]
}

export interface ProfileImportResult {
  fullName: string | null
  email: string | null
  location: string | null
  rawSkillMentions: string[]
  experiences: ProfileImportExperienceDraft[]
}

export type ScreeningDecision = 'PASS' | 'REVIEW' | 'BLOCK'

export interface JobPosting {
  id: string
  dedupeHash: string
  company: string
  title: string
  location: string
  remote: boolean
  compMinMinorUnits: number | null
  compMaxMinorUnits: number | null
  acceptedWorkAuthorizations: string[]
  mandatorySkillIds: string[]
  preferredSkillIds: string[]
  domain: string | null
  minYearsExperience: number
  rawDescription: string
  screeningDecision: ScreeningDecision
  screeningReasons: string
}

export interface MatchScorecard {
  id: string
  candidate: Candidate
  jobPosting: JobPosting
  hardEligibilityPassed: boolean
  skillScore: number
  experienceScore: number
  semanticScore: number
  domainScore: number
  compositeScore: number
  shortlisted: boolean
  reasonCodes: string[]
  decidedAt: string
}

export type TailoredArtifactStatus = 'PENDING_APPROVAL' | 'NEEDS_HUMAN_REVIEW' | 'APPROVED' | 'REJECTED'

export interface TailoredArtifact {
  id: string
  candidate: Candidate
  jobPosting: JobPosting
  content: string
  criticLoopsUsed: number
  groundingPassed: boolean
  rejectedClaims: string[]
  coverLetterContent: string | null
  /** Each entry is "question || answer" - see ResumeTailoringService. */
  screeningAnswers: string[]
  status: TailoredArtifactStatus
  generatedAt: string
  reviewedAt: string | null
  reviewNote: string | null
}

export interface ArtifactReviewView {
  artifact: TailoredArtifact
  matchScorecard: MatchScorecard | null
}

export type TailoringJobStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'

export interface TailoringJob {
  id: string
  candidate: Candidate
  jobPosting: JobPosting
  status: TailoringJobStatus
  resultArtifact: TailoredArtifact | null
  errorMessage: string | null
  submittedAt: string
  completedAt: string | null
}

export type AuditEventType =
  | 'JOB_POSTING_SCREENED'
  | 'MATCH_SCORED'
  | 'TAILORED_ARTIFACT_GENERATED'
  | 'TAILORED_ARTIFACT_REVIEWED'
  | 'CANDIDATE_DATA_ERASED'
  | 'AUTOPILOT_CYCLE_STARTED'
  | 'AUTOPILOT_APPLICATION_SUBMITTED'
  | 'AUTOPILOT_FOLLOW_UP_LOGGED'
  | 'AUTOPILOT_CYCLE_COMPLETED'
  | 'EMAIL_CLASSIFIED'
  | 'EMAIL_REPLY_REVIEWED'
  | 'CANDIDATE_SUBMITTED_APPLICATION'

export interface AuditEvent {
  id: string
  eventType: AuditEventType
  subjectId: string
  details: string
  occurredAt: string
  previousHash: string
  hash: string
}

export interface AuditVerifyResult {
  intact: boolean
  firstBrokenEventId?: string
}

export interface BiasGroupStat {
  category: string
  totalScored: number
  shortlisted: number
  shortlistRate: number
  avgCompositeScore: number
  impactRatio: number | null
}

export interface BiasAuditReport {
  byGender: BiasGroupStat[]
  byRaceEthnicity: BiasGroupStat[]
  generatedAt: string
}

export interface CandidateDemographics {
  id: string
  candidateId: string
  gender: string
  raceEthnicity: string
  veteranStatus: string
  disabilityStatus: string
  submittedAt: string
}

export interface DiscoveryRunResult {
  discovered: number
  ingested: number
}

export interface ConsistencyStat {
  mean: number
  stddev: number
  min: number
  max: number
}

export interface ConsistencyReport {
  runs: number
  semanticScore: ConsistencyStat
  domainScore: ConsistencyStat
  compositeScore: ConsistencyStat
}

// --- Career Vault ---

export interface Achievement {
  id: string
  title: string
  description: string | null
  occurredOn: string | null
  tags: string[]
}

export interface Education {
  id: string
  institution: string
  degree: string
  fieldOfStudy: string | null
  startDate: string | null
  endDate: string | null
}

export interface Certification {
  id: string
  name: string
  issuer: string
  issuedOn: string | null
  credentialId: string | null
}

export interface CandidateExperience {
  id: string
  employer: string
  title: string
  startDate: string
  endDate: string | null
  narrative: string
  verifiedMetrics: string[]
  skillIds: string[]
  rawSkillMentions: string[]
}

export interface CareerVaultView {
  candidate: Candidate
  experiences: CandidateExperience[]
  achievements: Achievement[]
  education: Education[]
  certifications: Certification[]
  completeness: number
}

// --- ATS scoring ---

export interface AtsCheck {
  label: string
  passed: boolean
}

export interface AtsScoreResult {
  score: number
  checks: AtsCheck[]
}

// --- Interview prep (AI mock interviews) ---

export type InterviewCategory = 'SYSTEM_DESIGN' | 'CODING' | 'BEHAVIORAL' | 'DOMAIN'

export type CareerLevel = 'ENTRY' | 'MID' | 'SENIOR' | 'STAFF_PLUS'

export type InterviewStage = 'PHONE_SCREEN' | 'TECHNICAL_DEEP_DIVE' | 'ONSITE_LOOP' | 'FINAL_BEHAVIORAL'

export interface InterviewQuestion {
  id: string
  category: InterviewCategory
  text: string
  generated: boolean
}

export interface AnswerFeedback {
  clarity: number
  depth: number
  relevance: number
  suggestion: string
  needsFollowUpProbability: number
  followUpQuestion: string | null
}

// --- Pipeline (interview/offer tracking) ---

export type PipelineInterviewMode = 'VIRTUAL' | 'ONSITE' | 'PHONE' | 'UNKNOWN'
export type PipelineInterviewStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED'
export type PipelineInterviewSource = 'MANUAL' | 'EMAIL_EXTRACTED'

export interface PipelineInterview {
  id: string
  candidate: Candidate
  jobPosting: JobPosting
  scheduledAt: string | null
  mode: PipelineInterviewMode
  status: PipelineInterviewStatus
  source: PipelineInterviewSource
  notes: string | null
  createdAt: string
}

export type OfferStatus = 'EXTENDED' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED'

export interface PipelineOffer {
  id: string
  candidate: Candidate
  jobPosting: JobPosting
  compensationMinorUnits: number | null
  status: OfferStatus
  notes: string | null
  receivedAt: string
}

export interface PipelineSummary {
  matched: number
  shortlisted: number
  tailoring: number
  pendingApproval: number
  approved: number
  interviews: number
  offers: number
}

// --- Autopilot ("AI Job Application Agent") ---
//
// Deliberately, explicitly built despite docs/00/docs/02's original HITL-by-default
// guarantee - built at the user's direction after being shown that tradeoff. "Apply"
// still never fires an outbound submission at a real third-party ATS; see the backend
// AutopilotService javadoc for the full scoping note. A high-priority match (composite
// score >= highPriorityReviewThreshold) still lands in the ordinary Applications review
// queue instead of auto-approving, matching the mock's own "review for high-priority
// roles" copy.

export type AutopilotRunStatus = 'STOPPED' | 'RUNNING' | 'PAUSED'

export type AutopilotStage = 'IDLE' | 'SEARCH_JOBS' | 'MATCH_AND_RANK' | 'CUSTOMIZE' | 'APPLY' | 'TRACK' | 'FOLLOW_UP'

export interface AutopilotSettings {
  id: string
  candidateId: string
  status: AutopilotRunStatus
  currentStage: AutopilotStage
  currentStageDetail: string
  lastRunAt: string | null
  nextRunAt: string | null
  preferredRoles: string[]
  preferredLocations: string[]
  experienceLevel: string
  jobType: string
  salaryMinMinorUnits: number | null
  salaryMaxMinorUnits: number | null
  remoteOnly: boolean
  openToRelocation: boolean
  includeGlobalOpportunities: boolean
  keySkills: string[]
  includeKeywords: string[]
  excludeKeywords: string[]
  enabledJobSources: string[]
  autoApply: boolean
  aiTailorResume: boolean
  generateCoverLetter: boolean
  autoFillForms: boolean
  skipSponsorshipRequired: boolean
  notifyBeforeApplying: boolean
  autoFollowUp: boolean
  dailyApplicationLimit: number
  highPriorityReviewThreshold: number
  notifyNewMatches: boolean
  notifyApplicationSubmitted: boolean
  notifyStatusChanges: boolean
  notifyInterviewInvitations: boolean
  notifyWeeklySummary: boolean
}

export type AutopilotSettingsUpdate = Partial<
  Pick<
    AutopilotSettings,
    | 'preferredRoles'
    | 'preferredLocations'
    | 'experienceLevel'
    | 'jobType'
    | 'salaryMinMinorUnits'
    | 'salaryMaxMinorUnits'
    | 'remoteOnly'
    | 'openToRelocation'
    | 'includeGlobalOpportunities'
    | 'keySkills'
    | 'includeKeywords'
    | 'excludeKeywords'
    | 'enabledJobSources'
    | 'autoApply'
    | 'aiTailorResume'
    | 'generateCoverLetter'
    | 'autoFillForms'
    | 'skipSponsorshipRequired'
    | 'notifyBeforeApplying'
    | 'autoFollowUp'
    | 'dailyApplicationLimit'
    | 'notifyNewMatches'
    | 'notifyApplicationSubmitted'
    | 'notifyStatusChanges'
    | 'notifyInterviewInvitations'
    | 'notifyWeeklySummary'
  >
>

export interface AutopilotStats {
  jobsScannedToday: number
  applicationsSubmittedToday: number
  goodMatchesFound: number
  companiesTargeted: number
  totalApplicationsSubmitted: number
  interviewsScheduled: number
}

export interface AutopilotStatusView {
  settings: AutopilotSettings
  stats: AutopilotStats
}

// --- AI config (read-only, Settings "AI Preferences" tab) ---

export interface AiProviderView {
  provider: string
  model: string
  configured: boolean
}

export interface AiConfigView {
  tailoringAndDrafting: AiProviderView
  embeddings: AiProviderView
  judgments: AiProviderView
}

// --- Manual application log ("+ Add Application") ---

export type ManualApplicationStatus = 'APPLIED' | 'INTERVIEWING' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'

export interface ManualApplicationTimelineEntry {
  id: string
  note: string
  occurredAt: string
}

export interface ManualApplication {
  id: string
  candidateId: string
  company: string
  role: string
  appliedDate: string | null
  status: ManualApplicationStatus
  notes: string | null
  timeline: ManualApplicationTimelineEntry[]
  createdAt: string
}

// --- Email intake ---

export type EmailType = 'INTERVIEW_INVITATION' | 'REJECTION' | 'FOLLOW_UP' | 'OTHER'

export interface EmailIntakeResult {
  emailType: EmailType
  relatedJobPostingId: string | null
  mode: PipelineInterviewMode
  suggestedScheduledAt: string | null
  createdInterviewId: string | null
  recordId: string
  draftReplyText: string | null
}

export type EmailReviewStatus = 'NO_REPLY_NEEDED' | 'NEEDS_REVIEW' | 'APPROVED_SEND' | 'EDITED_SEND' | 'SCHEDULED' | 'CANCELLED'

export interface EmailIntakeRecord {
  id: string
  candidate: Candidate
  jobPosting: JobPosting | null
  rawEmailText: string
  emailType: EmailType
  mode: PipelineInterviewMode
  suggestedScheduledAt: string | null
  createdInterviewId: string | null
  draftReplyText: string | null
  reviewStatus: EmailReviewStatus
  finalReplyText: string | null
  reviewedAt: string | null
  reviewNote: string | null
  classifiedAt: string
}
