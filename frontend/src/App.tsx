import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { CandidateProvider } from './context/CandidateContext'
import { AppShell } from './components/layout/AppShell'
import { Dashboard } from './pages/Dashboard'
import { JobDiscovery } from './pages/JobDiscovery'
import { Matches } from './pages/Matches'
import { MatchExplanation } from './pages/MatchExplanation'
import { Applications } from './pages/Applications'
import { Compliance } from './pages/Compliance'
import { CareerVault } from './pages/CareerVault'
import { ResumePreview } from './pages/ResumePreview'
import { InterviewPrep } from './pages/InterviewPrep'
import { Pipeline } from './pages/Pipeline'
import { Onboarding } from './pages/Onboarding'
import { BrowserAssist } from './pages/BrowserAssist'
import { Autopilot } from './pages/Autopilot'
import { AutopilotSettingsPage } from './pages/AutopilotSettings'
import { EmailInbox } from './pages/EmailInbox'
import { EmailReview } from './pages/EmailReview'
import { EvidenceGraph } from './pages/EvidenceGraph'
import { Settings } from './pages/Settings'

export default function App() {
  return (
    <CandidateProvider>
      <BrowserRouter>
        <AppShell>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/job-discovery" element={<JobDiscovery />} />
            <Route path="/matches" element={<Matches />} />
            <Route path="/matches/:id" element={<MatchExplanation />} />
            <Route path="/applications" element={<Applications />} />
            <Route path="/browser-assist/:artifactId" element={<BrowserAssist />} />
            <Route path="/career-vault" element={<CareerVault />} />
            <Route path="/career-vault/resume" element={<ResumePreview />} />
            <Route path="/career-vault/evidence/:jobPostingId" element={<EvidenceGraph />} />
            <Route path="/interview-prep" element={<InterviewPrep />} />
            <Route path="/pipeline" element={<Pipeline />} />
            <Route path="/autopilot" element={<Autopilot />} />
            <Route path="/autopilot/settings" element={<AutopilotSettingsPage />} />
            <Route path="/email-inbox" element={<EmailInbox />} />
            <Route path="/email-review" element={<EmailReview />} />
            <Route path="/compliance" element={<Compliance />} />
            <Route path="/onboarding" element={<Onboarding />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </AppShell>
      </BrowserRouter>
    </CandidateProvider>
  )
}
