import type {
  Achievement,
  AiConfigView,
  AiOpsSummary,
  AnswerFeedback,
  ArtifactReviewView,
  AtsScoreResult,
  AuditEvent,
  AuditVerifyResult,
  AutopilotSettings,
  AutopilotSettingsUpdate,
  AutopilotStatusView,
  ProfilePositioning,
  BiasAuditReport,
  Candidate,
  CandidateDemographics,
  CandidateExperience,
  CandidateProfileUpdate,
  CandidateRequest,
  CandidateSettings,
  CandidateSettingsUpdate,
  CareerLevel,
  CareerVaultView,
  Certification,
  ConsistencyReport,
  DiscoveryRunResult,
  DiscoverySourceStatus,
  Education,
  EmailIntakeRecord,
  EmailIntakeResult,
  InsightsSummary,
  ManualApplication,
  ManualApplicationStatus,
  InterviewCategory,
  InterviewQuestion,
  InterviewStage,
  JobPosting,
  MatchScorecard,
  OfferStatus,
  PhotoMeta,
  PipelineActivityItem,
  PipelineInterview,
  PipelineInterviewMode,
  PipelineInterviewStatus,
  PipelineOffer,
  PipelineSummary,
  ProfileImportResult,
  Project,
  ResumeMeta,
  SkillProfile,
  SkillProficiencyLevel,
  TailoringJob,
} from './types'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`/api${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  return handleResponse<T>(res)
}

/** Like `request`, but for a `FormData` body - omits the JSON `Content-Type` header so the
 * browser sets the multipart boundary itself. */
async function requestMultipart<T>(path: string, formData: FormData): Promise<T> {
  const res = await fetch(`/api${path}`, { method: 'POST', body: formData })
  return handleResponse<T>(res)
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text()
    let message = body
    try {
      message = JSON.parse(body).message ?? body
    } catch {
      // not JSON - use the raw body
    }
    throw new ApiError(res.status, message || res.statusText)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return (await res.json()) as T
}

export const api = {
  aiConfig: {
    get: () => request<AiConfigView>('/config/ai'),
  },
  aiOps: {
    summary: () => request<AiOpsSummary>('/ai-ops/summary'),
    insights: () => request<InsightsSummary>('/ai-ops/insights'),
  },
  candidates: {
    list: () => request<Candidate[]>('/candidates'),
    create: (body: CandidateRequest) => request<Candidate>('/candidates', { method: 'POST', body: JSON.stringify(body) }),
    get: (id: string) => request<Candidate>(`/candidates/${id}`),
    update: (id: string, body: CandidateProfileUpdate) =>
      request<Candidate>(`/candidates/${id}/profile`, { method: 'PUT', body: JSON.stringify(body) }),
    erase: (id: string) => request<void>(`/candidates/${id}`, { method: 'DELETE' }),
    export: (id: string) => request<unknown>(`/candidates/${id}/export`),
    uploadResume: (id: string, file: File) => {
      const formData = new FormData()
      formData.set('file', file)
      return requestMultipart<ResumeMeta>(`/candidates/${id}/resume`, formData)
    },
    resumeMeta: (id: string) => request<ResumeMeta>(`/candidates/${id}/resume/meta`),
    resumeDownloadUrl: (id: string) => `/api/candidates/${id}/resume`,
    resumePreviewUrl: (id: string) => `/api/candidates/${id}/resume?disposition=inline`,
    deleteResume: (id: string) => request<void>(`/candidates/${id}/resume`, { method: 'DELETE' }),
    uploadPhoto: (id: string, file: File | Blob) => {
      const formData = new FormData()
      formData.set('file', file, 'photo.png')
      return requestMultipart<PhotoMeta>(`/candidates/${id}/photo`, formData)
    },
    photoMeta: (id: string) => request<PhotoMeta>(`/candidates/${id}/photo/meta`),
    photoUrl: (id: string) => `/api/candidates/${id}/photo`,
    deletePhoto: (id: string) => request<void>(`/candidates/${id}/photo`, { method: 'DELETE' }),
    importResume: (file: File) => {
      const formData = new FormData()
      formData.set('file', file)
      return requestMultipart<ProfileImportResult>('/candidates/profile-import/resume', formData)
    },
    importLinkedInText: (text: string) =>
      request<ProfileImportResult>('/candidates/profile-import/linkedin-text', {
        method: 'POST',
        body: JSON.stringify({ text }),
      }),
  },
  settings: {
    get: (candidateId: string) => request<CandidateSettings>(`/candidates/${candidateId}/settings`),
    update: (candidateId: string, body: CandidateSettingsUpdate) =>
      request<CandidateSettings>(`/candidates/${candidateId}/settings`, { method: 'PUT', body: JSON.stringify(body) }),
  },
  demographics: {
    get: (candidateId: string) => request<CandidateDemographics>(`/candidates/${candidateId}/demographics`),
    submit: (candidateId: string, body: Partial<CandidateDemographics>) =>
      request<CandidateDemographics>(`/candidates/${candidateId}/demographics`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
  },
  jobPostings: {
    list: () => request<JobPosting[]>('/job-postings'),
    get: (id: string) => request<JobPosting>(`/job-postings/${id}`),
    similar: (id: string) => request<JobPosting[]>(`/job-postings/${id}/similar`),
  },
  matches: {
    listForCandidate: (candidateId: string) => request<MatchScorecard[]>(`/matches?candidateId=${candidateId}`),
    get: (id: string) => request<MatchScorecard>(`/matches/${id}`),
    evaluate: (candidateId: string, jobPostingId: string) =>
      request<MatchScorecard>(`/matches?candidateId=${candidateId}&jobPostingId=${jobPostingId}`, {
        method: 'POST',
      }),
  },
  tailoring: {
    submit: (candidateId: string, jobPostingId: string) =>
      request<TailoringJob>(`/tailoring?candidateId=${candidateId}&jobPostingId=${jobPostingId}`, {
        method: 'POST',
      }),
    getJob: (jobId: string) => request<TailoringJob>(`/tailoring/jobs/${jobId}`),
    listJobsForCandidate: (candidateId: string) =>
      request<TailoringJob[]>(`/tailoring/jobs?candidateId=${candidateId}`),
    pendingReview: () => request<ArtifactReviewView[]>('/tailoring/artifacts/pending-review'),
    history: () => request<ArtifactReviewView[]>('/tailoring/artifacts/history'),
    approve: (artifactId: string, note: string) =>
      request(`/tailoring/artifacts/${artifactId}/approve`, { method: 'POST', body: JSON.stringify({ note }) }),
    reject: (artifactId: string, note: string) =>
      request(`/tailoring/artifacts/${artifactId}/reject`, { method: 'POST', body: JSON.stringify({ note }) }),
    atsScore: (artifactId: string) => request<AtsScoreResult>(`/tailoring/artifacts/${artifactId}/ats-score`),
    getArtifact: (artifactId: string) => request<ArtifactReviewView>(`/tailoring/artifacts/${artifactId}`),
    markSubmitted: (artifactId: string) =>
      request<void>(`/tailoring/artifacts/${artifactId}/mark-submitted`, { method: 'POST' }),
  },
  discovery: {
    poll: () => request<DiscoveryRunResult>('/discovery/poll', { method: 'POST' }),
    sources: () => request<DiscoverySourceStatus[]>('/discovery/sources'),
  },
  audit: {
    events: () => request<AuditEvent[]>('/audit/events'),
    eventsForSubject: (subjectId: string) => request<AuditEvent[]>(`/audit/events/subject/${subjectId}`),
    verify: () => request<AuditVerifyResult>('/audit/verify'),
    biasReport: () => request<BiasAuditReport>('/audit/bias-report'),
    purge: (olderThanDays: number) =>
      request<{ purged: number }>(`/audit/retention/purge?olderThanDays=${olderThanDays}`, { method: 'POST' }),
    pipelineFeed: (limit = 50) => request<PipelineActivityItem[]>(`/audit/pipeline-feed?limit=${limit}`),
  },
  calibration: {
    consistency: (candidateId: string, jobPostingId: string, runs: number) =>
      request<ConsistencyReport>(
        `/calibration/consistency?candidateId=${candidateId}&jobPostingId=${jobPostingId}&runs=${runs}`,
        { method: 'POST' },
      ),
  },
  vault: {
    get: (candidateId: string) => request<CareerVaultView>(`/candidates/${candidateId}/vault`),
    addExperience: (
      candidateId: string,
      body: {
        employer: string
        title: string
        startDate?: string
        endDate?: string
        narrative?: string
        verifiedMetrics?: string[]
        rawSkillMentions?: string[]
      },
    ) => request<CandidateExperience>(`/candidates/${candidateId}/vault/experiences`, { method: 'POST', body: JSON.stringify(body) }),
    deleteExperience: (candidateId: string, experienceId: string) =>
      request<void>(`/candidates/${candidateId}/vault/experiences/${experienceId}`, { method: 'DELETE' }),
    addAchievement: (candidateId: string, body: { title: string; description?: string; occurredOn?: string; tags?: string[] }) =>
      request<Achievement>(`/candidates/${candidateId}/vault/achievements`, { method: 'POST', body: JSON.stringify(body) }),
    addEducation: (
      candidateId: string,
      body: { institution: string; degree: string; fieldOfStudy?: string; startDate?: string; endDate?: string },
    ) => request<Education>(`/candidates/${candidateId}/vault/education`, { method: 'POST', body: JSON.stringify(body) }),
    addCertification: (candidateId: string, body: { name: string; issuer: string; issuedOn?: string; credentialId?: string }) =>
      request<Certification>(`/candidates/${candidateId}/vault/certifications`, { method: 'POST', body: JSON.stringify(body) }),
    addProject: (
      candidateId: string,
      body: { title: string; description?: string; url?: string; startDate?: string; endDate?: string; technologies?: string[] },
    ) => request<Project>(`/candidates/${candidateId}/vault/projects`, { method: 'POST', body: JSON.stringify(body) }),
    upsertSkillProfile: (
      candidateId: string,
      body: {
        skillName: string
        proficiencyLevel?: SkillProficiencyLevel | null
        yearsOfExperience?: number | null
        confidenceScore?: number | null
        lastUsedOn?: string | null
        lastUsedVersion?: string | null
      },
    ) => request<SkillProfile>(`/candidates/${candidateId}/vault/skills`, { method: 'PUT', body: JSON.stringify(body) }),
  },
  interviewPrep: {
    questions: (opts: {
      category?: InterviewCategory
      candidateId?: string
      jobPostingId?: string
      careerLevel?: CareerLevel
      stage?: InterviewStage
    }) => {
      const params = new URLSearchParams()
      if (opts.category) params.set('category', opts.category)
      if (opts.candidateId) params.set('candidateId', opts.candidateId)
      if (opts.jobPostingId) params.set('jobPostingId', opts.jobPostingId)
      if (opts.careerLevel) params.set('careerLevel', opts.careerLevel)
      if (opts.stage) params.set('stage', opts.stage)
      const qs = params.toString()
      return request<InterviewQuestion[]>(`/interview-prep/questions${qs ? `?${qs}` : ''}`)
    },
    suggestedLevel: (candidateId: string) =>
      request<{ level: CareerLevel }>(`/interview-prep/suggested-level?candidateId=${candidateId}`),
    submitAnswer: (opts: {
      questionId?: string
      questionText?: string
      answer: string
      jobPostingId: string
      candidateId?: string
      careerLevel?: CareerLevel
      interviewStage?: InterviewStage
    }) =>
      request<AnswerFeedback>('/interview-prep/answers', {
        method: 'POST',
        body: JSON.stringify(opts),
      }),
  },
  pipeline: {
    summary: (candidateId: string) => request<PipelineSummary>(`/candidates/${candidateId}/pipeline-summary`),
    interviews: (candidateId: string) => request<PipelineInterview[]>(`/candidates/${candidateId}/interviews`),
    scheduleInterview: (
      candidateId: string,
      body: { jobPostingId: string; scheduledAt?: string; mode?: PipelineInterviewMode; notes?: string },
    ) => request<PipelineInterview>(`/candidates/${candidateId}/interviews`, { method: 'POST', body: JSON.stringify(body) }),
    updateInterviewStatus: (candidateId: string, interviewId: string, status: PipelineInterviewStatus) =>
      request<PipelineInterview>(`/candidates/${candidateId}/interviews/${interviewId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status }),
      }),
    offers: (candidateId: string) => request<PipelineOffer[]>(`/candidates/${candidateId}/offers`),
    recordOffer: (candidateId: string, body: { jobPostingId: string; compensationMinorUnits?: number; currency?: string; notes?: string }) =>
      request<PipelineOffer>(`/candidates/${candidateId}/offers`, { method: 'POST', body: JSON.stringify(body) }),
    updateOfferStatus: (candidateId: string, offerId: string, status: OfferStatus) =>
      request<PipelineOffer>(`/candidates/${candidateId}/offers/${offerId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status }),
      }),
    manualApplications: (candidateId: string) => request<ManualApplication[]>(`/candidates/${candidateId}/manual-applications`),
    addManualApplication: (candidateId: string, body: { company: string; role: string; appliedDate?: string; notes?: string }) =>
      request<ManualApplication>(`/candidates/${candidateId}/manual-applications`, { method: 'POST', body: JSON.stringify(body) }),
    updateManualApplicationStatus: (candidateId: string, applicationId: string, status: ManualApplicationStatus, note = '') =>
      request<ManualApplication>(`/candidates/${candidateId}/manual-applications/${applicationId}/status`, {
        method: 'PUT',
        body: JSON.stringify({ status, note }),
      }),
    addManualApplicationTimelineNote: (candidateId: string, applicationId: string, note: string) =>
      request<ManualApplication>(`/candidates/${candidateId}/manual-applications/${applicationId}/timeline`, {
        method: 'POST',
        body: JSON.stringify({ note }),
      }),
  },
  emailIntake: {
    classify: (candidateId: string, rawEmailText: string) =>
      request<EmailIntakeResult>(`/candidates/${candidateId}/email-intake`, {
        method: 'POST',
        body: JSON.stringify({ rawEmailText }),
      }),
    history: (candidateId: string) => request<EmailIntakeRecord[]>(`/candidates/${candidateId}/email-intake/history`),
    needsReview: (candidateId: string) => request<EmailIntakeRecord[]>(`/candidates/${candidateId}/email-intake/needs-review`),
    thread: (candidateId: string, jobPostingId: string) =>
      request<EmailIntakeRecord[]>(`/candidates/${candidateId}/email-intake/thread?jobPostingId=${jobPostingId}`),
    suggestAlternative: (candidateId: string, recordId: string, alternativeText: string, note = '') =>
      request<EmailIntakeRecord>(`/candidates/${candidateId}/email-intake/${recordId}/suggest-alternative`, {
        method: 'POST',
        body: JSON.stringify({ alternativeText, note }),
      }),
    approveSend: (candidateId: string, recordId: string, note = '') =>
      request<EmailIntakeRecord>(`/candidates/${candidateId}/email-intake/${recordId}/approve-send`, {
        method: 'POST',
        body: JSON.stringify({ note }),
      }),
    editAndSend: (candidateId: string, recordId: string, editedText: string, note = '') =>
      request<EmailIntakeRecord>(`/candidates/${candidateId}/email-intake/${recordId}/edit-send`, {
        method: 'POST',
        body: JSON.stringify({ editedText, note }),
      }),
    schedule: (candidateId: string, recordId: string, note = '') =>
      request<EmailIntakeRecord>(`/candidates/${candidateId}/email-intake/${recordId}/schedule`, {
        method: 'POST',
        body: JSON.stringify({ note }),
      }),
    cancel: (candidateId: string, recordId: string, note = '') =>
      request<EmailIntakeRecord>(`/candidates/${candidateId}/email-intake/${recordId}/cancel`, {
        method: 'POST',
        body: JSON.stringify({ note }),
      }),
  },
  autopilot: {
    getSettings: (candidateId: string) => request<AutopilotSettings>(`/candidates/${candidateId}/autopilot/settings`),
    updateSettings: (candidateId: string, body: AutopilotSettingsUpdate) =>
      request<AutopilotSettings>(`/candidates/${candidateId}/autopilot/settings`, {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    status: (candidateId: string) => request<AutopilotStatusView>(`/candidates/${candidateId}/autopilot/status`),
    start: (candidateId: string) => request<AutopilotSettings>(`/candidates/${candidateId}/autopilot/start`, { method: 'POST' }),
    pause: (candidateId: string) => request<AutopilotSettings>(`/candidates/${candidateId}/autopilot/pause`, { method: 'POST' }),
    stop: (candidateId: string) => request<AutopilotSettings>(`/candidates/${candidateId}/autopilot/stop`, { method: 'POST' }),
    runNow: (candidateId: string) => request<AutopilotSettings>(`/candidates/${candidateId}/autopilot/run-now`, { method: 'POST' }),
    activity: (candidateId: string, limit = 50) =>
      request<AuditEvent[]>(`/candidates/${candidateId}/autopilot/activity?limit=${limit}`),
  },
  profilePositioning: {
    get: (candidateId: string) => request<ProfilePositioning>(`/candidates/${candidateId}/profile-positioning`),
  },
}
