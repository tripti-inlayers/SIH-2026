import { useState, useEffect } from 'react';
import { 
  ShieldAlert, ShieldCheck, AlertTriangle, RefreshCw, 
  Globe, Database, Key, HelpCircle, Cpu, UserCheck, 
  AlertOctagon, CheckCircle2, Search, ArrowRight, Info, 
  PhoneCall, AlertCircle, Building2,
  Menu, X, Smartphone, BarChart3
} from 'lucide-react';
import './App.css';

interface Signal {
  source: string;
  description: string;
  confidence: number;
  weight: number;
}

interface AnalyzeResponse {
  risk_score: number;
  risk_level: string;
  decision: string;
  signals: Signal[];
  explanation: string;
  partial_analysis: boolean;
}

interface InboxMessage {
  id: string;
  sender: string;
  content: string;
  date: number;
  result?: AnalyzeResponse;
  isAnalyzing?: boolean;
  isLive?: boolean;
}

const PRESETS = [
  {
    label: "Citizen Routine SMS",
    category: "Legitimate",
    sender: "+919876543210",
    text: "Hey, are we still meeting for lunch today? Let me know."
  },
  {
    label: "DLT Verified Bank OTP",
    category: "Official Govt/Bank",
    sender: "VK-SBIIN",
    text: "Your SBI account OTP for transaction of Rs 4,500 is 849201. Do not share OTP with anyone."
  },
  {
    label: "Unregistered Bank Impersonation",
    category: "Phishing Fraud",
    sender: "SBI-SUPPORT",
    text: "URGENT! Your bank account has been blocked. Verify account details immediately at http://safe-login.com or call +1234567890"
  },
  {
    label: "Suspicious TLD Link",
    category: "Malicious Lure",
    sender: "+919123456780",
    text: "Check out this new scholarship game at http://cool-game.tk"
  },
  {
    label: "Lottery Blacklist Threat",
    category: "Financial Scam",
    sender: "LOTTERY-ALERT",
    text: "Get your lottery prize of 1 million at http://malicious.com"
  },
  {
    label: "SSRF Infrastructure Attack",
    category: "Cyber Attack",
    sender: "127.0.0.1",
    text: "Admin dashboard is at http://127.0.0.1/admin"
  }
];

// Add a global type declaration for the JavascriptInterface bridging
declare global {
  interface Window {
    onSmsReceived?: (payloadJson: string) => void;
    onNativeBridgeReady?: (isPermissionGranted: boolean) => void;
    AndroidNativeBridge?: {
      isNative: () => boolean;
      checkSmsPermission: () => boolean;
      requestSmsPermission: () => void;
      getInboxSms: (limit: number) => string;
    };
  }
}

const SAMPLE_INBOX_MESSAGES: InboxMessage[] = [
  {
    id: "sample-1",
    sender: "VM-SBIINB",
    content: "Dear Customer, Your SBI NetBanking access is suspended due to pending KYC. Click http://bit.ly/sbi-kyc-update immediately to restore.",
    date: Date.now() - 1000 * 60 * 15,
  },
  {
    id: "sample-2",
    sender: "AD-INDPOST",
    content: "Your IndiaPost consignment #IN849204819 has arrived at the regional hub and is out for delivery today.",
    date: Date.now() - 1000 * 60 * 120,
  },
  {
    id: "sample-3",
    sender: "+919876501234",
    content: "Electricity bill unpaid! Power will be disconnected tonight at 9:30 PM. Call power officer at 9876501234 or download update apk.",
    date: Date.now() - 1000 * 60 * 360,
  },
  {
    id: "sample-4",
    sender: "JX-HDFCBK",
    content: "Rs. 2,450.00 spent on your HDFC Bank Card ending 9102 at RELIANCE RETAIL on 28-AUG. Avail Bal: Rs 48,210.00.",
    date: Date.now() - 1000 * 60 * 720,
  },
  {
    id: "sample-5",
    sender: "+917012345678",
    content: "Part-time work from home! Earn Rs. 3000-8000 daily by liking YouTube videos. Contact HR on Telegram: https://t.me/quickjob_india",
    date: Date.now() - 1000 * 60 * 1440,
  }
];

export default function App() {
  const [content, setContent] = useState('');
  const [sender, setSender] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [result, setResult] = useState<AnalyzeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isBypassed, setIsBypassed] = useState(false);
  const [reportStatus, setReportStatus] = useState<string | null>(null);
  const [fontSizeClass, setFontSizeClass] = useState<'normal' | 'large' | 'larger'>('normal');
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false);
  const [mobileActiveTab, setMobileActiveTab] = useState<'input' | 'inbox' | 'results'>('input');
  
  // Native Android Shell & SMS Integration States
  const [isNativeApp, setIsNativeApp] = useState(false);
  const [smsPermissionGranted, setSmsPermissionGranted] = useState<boolean | null>(null);
  const [interceptedNotification, setInterceptedNotification] = useState<{ sender: string; text: string } | null>(null);
  
  // SMS Inbox State
  const [inboxList, setInboxList] = useState<InboxMessage[]>([]);
  const [isLoadingInbox, setIsLoadingInbox] = useState(false);
  const [isBatchScanning, setIsBatchScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState<{ current: number; total: number }>({ current: 0, total: 0 });

  const handleAnalyze = async (textToAnalyze?: string, senderToAnalyze?: string) => {
    const text = textToAnalyze !== undefined ? textToAnalyze : content;
    const sendVal = senderToAnalyze !== undefined ? senderToAnalyze : sender;
    if (!text.trim()) return;

    setIsLoading(true);
    setError(null);
    setIsBypassed(false);
    setReportStatus(null);
    setMobileActiveTab('results'); // Auto-switch to results tab on mobile screen

    try {
      const response = await fetch('http://localhost:8000/api/v1/analyze', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ 
          content: text, 
          source: 'manual',
          sender: sendVal || undefined 
        }),
      });

      if (!response.ok) {
        throw new Error('Analysis request failed. Please check backend connection.');
      }

      const data = await response.json();
      setResult(data);
    } catch (err: any) {
      setError(err.message || 'Something went wrong while querying server.');
      setResult(null);
    } finally {
      setIsLoading(false);
    }
  };

  const handleReport = async (type: 'spam_report' | 'bypass_report') => {
    if (!result) return;
    try {
      const response = await fetch('http://localhost:8000/api/v1/report', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          content,
          decision: result.decision,
          risk_score: result.risk_score,
          signals: result.signals,
          report_type: type
        }),
      });

      if (!response.ok) {
        throw new Error('Reporting failed');
      }

      if (type === 'spam_report') {
        setReportStatus('Incident successfully logged in National Telecom Security Registry.');
      } else {
        setIsBypassed(true);
        setReportStatus('Bypass acknowledged and recorded for audit verification.');
      }
    } catch (err: any) {
      setError('Failed to log report/bypass: ' + err.message);
    }
  };

  const fetchInbox = () => {
    setIsLoadingInbox(true);
    try {
      if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.getInboxSms === 'function') {
        const rawJson = window.AndroidNativeBridge.getInboxSms(30);
        const parsed: any[] = JSON.parse(rawJson);
        if (parsed && parsed.length > 0) {
          setInboxList(parsed.map(item => ({
            id: String(item.id || Math.random()),
            sender: item.sender || 'Unknown',
            content: item.content || '',
            date: item.date || Date.now(),
          })));
          setIsLoadingInbox(false);
          return;
        }
      }
    } catch (e) {
      console.error("Failed to read native inbox", e);
    }
    // Fallback to rich sample messages if empty or testing in browser
    setInboxList(prev => prev.length > 0 ? prev : SAMPLE_INBOX_MESSAGES);
    setIsLoadingInbox(false);
  };

  const scanSingleSms = async (msg: InboxMessage, switchToAudit = false) => {
    setInboxList(prev => prev.map(m => m.id === msg.id ? { ...m, isAnalyzing: true } : m));
    
    try {
      const response = await fetch('http://localhost:8000/api/v1/analyze', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          content: msg.content,
          source: 'sms',
          sender: msg.sender || undefined
        })
      });
      if (response.ok) {
        const data: AnalyzeResponse = await response.json();
        setInboxList(prev => prev.map(m => m.id === msg.id ? { ...m, result: data, isAnalyzing: false } : m));
        if (switchToAudit) {
          setContent(msg.content);
          setSender(msg.sender);
          setResult(data);
          setMobileActiveTab('results');
        }
        return data;
      }
    } catch (e) {
      console.error("Failed to scan SMS", e);
    } finally {
      setInboxList(prev => prev.map(m => m.id === msg.id ? { ...m, isAnalyzing: false } : m));
    }
  };

  const scanAllInbox = async () => {
    if (isBatchScanning || inboxList.length === 0) return;
    setIsBatchScanning(true);
    const unanalyzed = inboxList.filter(m => !m.result);
    setScanProgress({ current: 0, total: unanalyzed.length || inboxList.length });
    
    let currentCount = 0;
    for (const msg of inboxList) {
      if (!msg.result) {
        await scanSingleSms(msg, false);
        currentCount++;
        setScanProgress({ current: currentCount, total: unanalyzed.length || inboxList.length });
      }
    }
    setIsBatchScanning(false);
  };

  useEffect(() => {
    fetchInbox();

    // 1. Check if running inside Android Native Hybrid Shell
    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.checkSmsPermission === 'function') {
      setIsNativeApp(true);
      const isGranted = window.AndroidNativeBridge.checkSmsPermission();
      setSmsPermissionGranted(isGranted);
      if (isGranted) {
        fetchInbox();
      }
    }

    // 2. Callback from Android MainActivity on page load / on resume
    window.onNativeBridgeReady = (isGranted: boolean) => {
      setIsNativeApp(true);
      setSmsPermissionGranted(isGranted);
      if (isGranted) {
        fetchInbox();
      }
    };

    // 3. Expose global receiver for real-time incoming SMS
    window.onSmsReceived = (payloadJson: string) => {
      try {
        const payload = JSON.parse(payloadJson);
        const text = payload.content || '';
        const sendVal = payload.sender || '';
        
        const newLiveMessage: InboxMessage = {
          id: 'live-' + Date.now(),
          sender: sendVal,
          content: text,
          date: Date.now(),
          isLive: true,
          isAnalyzing: true
        };

        setInboxList(prev => [newLiveMessage, ...prev]);
        setContent(text);
        setSender(sendVal);
        setInterceptedNotification({ sender: sendVal, text });

        // Analyze and attach result
        handleAnalyze(text, sendVal).then(() => {
          setInboxList(prev => prev.map(m => m.id === newLiveMessage.id ? { ...m, isAnalyzing: false } : m));
        });
      } catch (e) {
        console.error("Failed to parse incoming SMS payload", e);
      }
    };

    return () => {
      delete window.onSmsReceived;
      delete window.onNativeBridgeReady;
    };
  }, []);

  const selectPreset = (text: string, sendVal: string) => {
    setContent(text);
    setSender(sendVal);
    handleAnalyze(text, sendVal);
  };

  const getSourceIcon = (source: string) => {
    switch (source) {
      case 'ml_model':
        return <Cpu size={18} className="text-primary" />;
      case 'keyword_analyzer':
        return <Key size={18} className="text-primary" />;
      case 'url_lexical':
        return <Globe size={18} className="text-primary" />;
      case 'threat_intel':
        return <Database size={18} className="text-primary" />;
      case 'identity_verifier':
        return <UserCheck size={18} className="text-primary" />;
      default:
        return <HelpCircle size={18} className="text-primary" />;
    }
  };

  const getSourceLabel = (source: string) => {
    switch (source) {
      case 'ml_model':
        return 'RoBERTa Semantic Analysis';
      case 'keyword_analyzer':
        return 'Urgency & Financial Heuristics';
      case 'url_lexical':
        return 'Lexical URL & TLD Scanner';
      case 'threat_intel':
        return 'Google Web Risk & Threat Intel';
      case 'identity_verifier':
        return 'TRAI DLT Header Verification';
      default:
        return source;
    }
  };

  const handleDismiss = () => {
    setContent('');
    setSender('');
    setResult(null);
    setIsBypassed(false);
    setReportStatus(null);
  };

  return (
    <div className={`gov-root font-size-${fontSizeClass}`}>
      
      {/* 1. TOP UTILITY STRIP (NSP Style Government Header Bar) */}
      <div className="gov-top-bar">
        <div className="gov-container top-bar-content">
          <div className="govt-identity">
            <span className="emblem-text">भारत सरकार | Government of India</span>
            <span className="ministry-text">Ministry of Communications &bull; Department of Telecommunications</span>
          </div>
          <div className="top-accessibility-links">
            <button onClick={() => setFontSizeClass('normal')} className="acc-btn">A-</button>
            <button onClick={() => setFontSizeClass('large')} className="acc-btn">A</button>
            <button onClick={() => setFontSizeClass('larger')} className="acc-btn">A+</button>
            <span className="lang-pill">English</span>
          </div>
        </div>
      </div>

      {/* 2. MAIN LOGO & BRANDING BAR (NSP White Header with Emblems & Title) */}
      <header className="gov-main-header">
        <div className="gov-container header-layout">
          <div className="header-brand-group">
            <div className="emblem-placeholder">
              <Building2 size={36} className="emblem-icon" />
            </div>
            <div className="portal-titles">
              <h1 className="portal-main-title">संचार साथी &bull; SANCHAR SAATHI</h1>
              <p className="portal-sub-title">Citizen Telecom Security, Phishing & Threat Mitigation Portal</p>
            </div>
          </div>

          <div className="header-badges">
            <div className="nic-badge">
              <span className="badge-title">AI Powered</span>
              <span className="badge-desc">RoBERTa &bull; DLT</span>
            </div>
            <div className="digital-india-badge">
              <span className="badge-title">National Portal</span>
              <span className="badge-desc">SLA Guarded</span>
            </div>
          </div>
        </div>
      </header>

      {/* 3. PRIMARY NAVIGATION BAR (NSP Navy Blue Tab Bar with Mobile Hamburger) */}
      <nav className="gov-navbar">
        <div className="gov-container nav-container">
          <div className="mobile-nav-toggle-row">
            <button 
              className="mobile-hamburger-btn"
              onClick={() => setIsMobileNavOpen(!isMobileNavOpen)}
              aria-label="Toggle mobile menu"
            >
              {isMobileNavOpen ? <X size={20} /> : <Menu size={20} />}
              <span>Menu</span>
            </button>
            <div className="emergency-contact">
              <PhoneCall size={14} /> <span>1930 / 1800-11-0031</span>
            </div>
          </div>

          <ul className={`nav-menu ${isMobileNavOpen ? 'mobile-open' : ''}`}>
            <li className="nav-item active"><a href="#home" onClick={() => setIsMobileNavOpen(false)}>Home</a></li>
            <li className="nav-item"><a href="#scanner" onClick={() => setIsMobileNavOpen(false)}>Threat Scanner</a></li>
            <li className="nav-item"><a href="#dlt" onClick={() => setIsMobileNavOpen(false)}>DLT Header Directory</a></li>
            <li className="nav-item"><a href="#schemes" onClick={() => setIsMobileNavOpen(false)}>Citizen Advisories</a></li>
            <li className="nav-item"><a href="#helpdesk" onClick={() => setIsMobileNavOpen(false)}>Helpdesk & Circulars</a></li>
          </ul>
        </div>
      </nav>

      {/* 4. ANNOUNCEMENT TICKER (NSP Style Orange/Blue News Bar) */}
      <div className="gov-announcement-strip">
        <div className="gov-container marquee-wrapper">
          <span className="announcement-tag">ADVISORY:</span>
          <span className="announcement-text">
            Never share OTPs or click unverified links. Verify Principal Entity SMS headers against the National DLT Register.
          </span>
        </div>
      </div>

      {/* NATIVE REAL-TIME SMS LISTENER STATUS BADGE */}
      {isNativeApp && (
        <div className="gov-container">
          {smsPermissionGranted ? (
            <div className="native-sms-status-bar active">
              <span className="live-pulse-dot"></span>
              <span className="status-text">
                <strong>Real-Time SMS Shield Active:</strong> App is monitoring incoming SMS for phishing and malicious URLs.
              </span>
            </div>
          ) : (
            <div 
              className="native-sms-status-bar warning" 
              onClick={() => window.AndroidNativeBridge?.requestSmsPermission()}
              role="button"
              tabIndex={0}
            >
              <AlertTriangle size={18} />
              <span className="status-text">
                <strong>SMS Interception Permission Required:</strong> Tap here to allow automatic SMS threat protection.
              </span>
            </div>
          )}
        </div>
      )}

      {/* LIVE INTERCEPTED SMS TOAST BANNER */}
      {interceptedNotification && (
        <div className="gov-container">
          <div className="sms-intercepted-toast">
            <div className="toast-top-line">
              <div className="toast-left">
                <Smartphone size={16} className="toast-icon" />
                <span><strong>Live SMS Intercepted:</strong> From <span className="toast-sender">{interceptedNotification.sender || 'Unknown'}</span></span>
              </div>
              <button 
                className="toast-close-btn" 
                onClick={() => setInterceptedNotification(null)}
                aria-label="Dismiss"
              >
                <X size={14} />
              </button>
            </div>
            <p className="toast-snippet">"{interceptedNotification.text}"</p>
          </div>
        </div>
      )}

      {/* SEGMENTED VIEW SWITCHER */}
      <div className="mobile-tab-strip gov-container">
        <button 
          className={`mobile-tab-btn ${mobileActiveTab === 'input' ? 'active' : ''}`}
          onClick={() => setMobileActiveTab('input')}
        >
          <Search size={15} /> 1. Manual Check
        </button>
        <button 
          className={`mobile-tab-btn ${mobileActiveTab === 'inbox' ? 'active' : ''}`}
          onClick={() => setMobileActiveTab('inbox')}
        >
          <Smartphone size={15} /> 2. Phone SMS Inbox ({inboxList.length})
          {inboxList.some(m => m.result?.decision === 'BLOCK') && (
            <span className="tab-danger-dot"></span>
          )}
        </button>
        <button 
          className={`mobile-tab-btn ${mobileActiveTab === 'results' ? 'active' : ''}`}
          onClick={() => setMobileActiveTab('results')}
        >
          <BarChart3 size={15} /> 3. Security Audit {result ? `(${result.decision})` : ''}
        </button>
      </div>

      {/* 5. MAIN CONTENT DASHBOARD */}
      <main className="gov-container main-body-layout">
        
        {/* TAB 1: Input Verification Form & Presets */}
        <section className={`portal-card input-card ${mobileActiveTab === 'input' ? 'mobile-visible' : 'mobile-hidden'}`}>
          <div className="card-header">
            <h2>SMS / Link Security Verification</h2>
            <p className="card-subtext">Enter sender alphanumeric ID and message text for multi-tier inspection.</p>
          </div>

          <div className="card-body">
            
            {/* Sender / DLT input */}
            <div className="form-group">
              <label htmlFor="sender-input" className="form-label">
                Sender ID / Telecom DLT Header <span className="text-muted">(Optional)</span>
              </label>
              <input 
                id="sender-input"
                type="text" 
                className="gov-input"
                placeholder="e.g. AX-HDFCBK, VM-SBIINB, +919876543210"
                value={sender}
                onChange={(e) => setSender(e.target.value)}
              />
              <span className="field-hint">Indian telecom headers are verified against authorized Principal Entities.</span>
            </div>

            {/* Message content textarea */}
            <div className="form-group">
              <label htmlFor="content-input" className="form-label">
                SMS Content or Suspicious URL <span className="text-danger">*</span>
              </label>
              <textarea 
                id="content-input"
                rows={5}
                className="gov-textarea"
                placeholder="Paste the SMS text, phishing link, or notification message here..."
                value={content}
                onChange={(e) => setContent(e.target.value)}
              />
              <div className="char-count-row">
                <span className="char-count">{content.length} characters</span>
                <span className="inspection-notice">Real-time heuristics & NLP enabled</span>
              </div>
            </div>

            {/* Action buttons */}
            <div className="form-actions">
              <button 
                onClick={() => handleAnalyze()}
                disabled={isLoading || !content.trim()}
                className="gov-btn btn-primary"
              >
                {isLoading ? (
                  <>
                    <RefreshCw className="animate-spin" size={16} /> Evaluating Security Topology...
                  </>
                ) : (
                  <>
                    <Search size={16} /> Verify Security & Safety
                  </>
                )}
              </button>

              <button 
                onClick={handleDismiss} 
                className="gov-btn btn-secondary"
                disabled={!content && !sender && !result}
              >
                Reset Form
              </button>
            </div>

            {/* Official Preset Scenarios */}
            <div className="portal-presets-section">
              <h3 className="presets-heading">Common Telemetry Test Scenarios</h3>
              <div className="presets-list">
                {PRESETS.map((p, idx) => (
                  <div 
                    key={idx} 
                    className="preset-item"
                    onClick={() => selectPreset(p.text, p.sender)}
                  >
                    <div className="preset-meta">
                      <span className="preset-name">{p.label}</span>
                      <span className="preset-category">{p.category}</span>
                    </div>
                    <ArrowRight size={14} className="preset-arrow" />
                  </div>
                ))}
              </div>
            </div>

          </div>
        </section>

        {/* TAB 2: On-Device SMS Inbox & Threat Scanner */}
        <section className={`portal-card inbox-card ${mobileActiveTab === 'inbox' ? 'mobile-visible' : 'mobile-hidden'}`}>
          <div className="card-header inbox-header">
            <div>
              <h2>📱 On-Device SMS Inbox Scanner</h2>
              <p className="card-subtext">Inspect stored device messages for smishing, suspicious links, and unverified headers.</p>
            </div>
            <div className="inbox-header-actions">
              <button 
                onClick={scanAllInbox} 
                disabled={isBatchScanning || inboxList.length === 0}
                className="gov-btn btn-primary btn-sm"
              >
                {isBatchScanning ? (
                  <>
                    <RefreshCw className="animate-spin" size={14} /> Scanning ({scanProgress.current}/{scanProgress.total})...
                  </>
                ) : (
                  <>
                    <ShieldAlert size={14} /> ⚡ Scan Entire Inbox
                  </>
                )}
              </button>
              <button 
                onClick={fetchInbox} 
                disabled={isLoadingInbox || isBatchScanning}
                className="gov-btn btn-secondary btn-sm"
                title="Reload Inbox"
              >
                <RefreshCw className={isLoadingInbox ? "animate-spin" : ""} size={14} />
              </button>
            </div>
          </div>

          <div className="card-body">
            
            {/* Inbox Threat Statistics Strip */}
            <div className="inbox-metrics-row">
              <div className="inbox-stat-item">
                <span className="inbox-stat-val">{inboxList.length}</span>
                <span className="inbox-stat-lbl">Total SMS</span>
              </div>
              <div className="inbox-stat-item green">
                <span className="inbox-stat-val">{inboxList.filter(m => m.result?.decision === 'ALLOW').length}</span>
                <span className="inbox-stat-lbl">Verified Safe</span>
              </div>
              <div className="inbox-stat-item amber">
                <span className="inbox-stat-val">{inboxList.filter(m => m.result?.decision === 'WARN').length}</span>
                <span className="inbox-stat-lbl">Suspicious</span>
              </div>
              <div className="inbox-stat-item red">
                <span className="inbox-stat-val">{inboxList.filter(m => m.result?.decision === 'BLOCK').length}</span>
                <span className="inbox-stat-lbl">Threats Flagged</span>
              </div>
            </div>

            {/* List of Messages */}
            <div className="inbox-messages-list">
              {inboxList.length === 0 ? (
                <div className="empty-inbox-state">
                  <Smartphone size={32} className="text-muted" />
                  <p>No SMS messages detected on device.</p>
                  <button onClick={fetchInbox} className="gov-btn btn-primary btn-sm">Reload SMS Inbox</button>
                </div>
              ) : (
                inboxList.map((msg) => {
                  const decision = msg.result?.decision;
                  let cardClass = "inbox-msg-card";
                  if (decision === 'BLOCK') cardClass += " threat-block";
                  else if (decision === 'WARN') cardClass += " threat-warn";
                  else if (decision === 'ALLOW') cardClass += " threat-allow";
                  if (msg.isLive) cardClass += " live-received";

                  return (
                    <div key={msg.id} className={cardClass}>
                      <div className="msg-card-top">
                        <div className="msg-sender-info">
                          <span className="msg-sender-badge">{msg.sender || 'Unknown'}</span>
                          {msg.isLive && <span className="msg-live-pill">LIVE / JUST IN</span>}
                          <span className="msg-date">
                            {new Date(msg.date).toLocaleDateString([], { month: 'short', day: 'numeric' })} {' '}
                            {new Date(msg.date).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                        </div>

                        {/* Status / Flagging Pill */}
                        <div className="msg-status-container">
                          {msg.isAnalyzing ? (
                            <span className="status-pill analyzing">
                              <RefreshCw className="animate-spin" size={12} /> Inspecting...
                            </span>
                          ) : msg.result ? (
                            <button 
                              className={`status-pill decision-${decision?.toLowerCase()}`}
                              onClick={() => {
                                setContent(msg.content);
                                setSender(msg.sender);
                                setResult(msg.result!);
                                setMobileActiveTab('results');
                              }}
                              title="Click to view deep audit report"
                            >
                              {decision === 'BLOCK' && <ShieldAlert size={13} />}
                              {decision === 'WARN' && <AlertTriangle size={13} />}
                              {decision === 'ALLOW' && <ShieldCheck size={13} />}
                              <span>{decision} ({Math.round(msg.result.risk_score)}/100)</span>
                            </button>
                          ) : (
                            <button 
                              className="status-pill unverified"
                              onClick={() => scanSingleSms(msg, true)}
                            >
                              <Search size={12} /> Verify Security
                            </button>
                          )}
                        </div>
                      </div>

                      <p className="msg-content-text">{msg.content}</p>

                      {/* Flag Explanation Footer */}
                      {msg.result && (
                        <div className="msg-explanation-bar">
                          <span className="exp-label">Analysis:</span>
                          <span className="exp-text">{msg.result.explanation}</span>
                          <button 
                            className="view-audit-link"
                            onClick={() => {
                              setContent(msg.content);
                              setSender(msg.sender);
                              setResult(msg.result!);
                              setMobileActiveTab('results');
                            }}
                          >
                            View Audit Breakdown &rarr;
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })
              )}
            </div>

          </div>
        </section>

        {/* RIGHT COLUMN: Official Verification & Signal Report */}
        <section className={`portal-card results-card ${mobileActiveTab === 'results' ? 'mobile-visible' : 'mobile-hidden'}`}>
          <div className="card-header result-card-header">
            <h2>Inspection Audit & Risk Analysis</h2>
            <span className="audit-status-badge">Official Telemetry</span>
          </div>

          <div className="card-body">
            
            {/* Loading State */}
            {isLoading && (
              <div className="gov-status-box loading-box">
                <RefreshCw className="animate-spin text-primary" size={36} />
                <h3>Analyzing Multi-Signal Vectors...</h3>
                <p>Running RoBERTa inference, validating DLT headers, checking domain entropy & reputation feeds.</p>
              </div>
            )}

            {/* Error State */}
            {!isLoading && error && (
              <div className="gov-status-box error-box">
                <AlertCircle size={36} className="text-danger" />
                <h3>Analysis Query Failed</h3>
                <p>{error}</p>
              </div>
            )}

            {/* Empty State */}
            {!isLoading && !result && !error && (
              <div className="gov-status-box empty-box">
                <Info size={40} className="text-primary-light" />
                <h3>Awaiting Input Submission</h3>
                <p>Submit a message or select a test scenario from the left panel to display the threat telemetry report.</p>
              </div>
            )}

            {/* Active Analysis Result */}
            {!isLoading && result && (
              <div className="audit-report-container">
                
                {/* Degradation Notice */}
                {result.partial_analysis && (
                  <div className="degraded-alert">
                    <AlertOctagon size={16} />
                    <span>Degraded Mode: Certain upstream services timed out; active weights redistributed safely.</span>
                  </div>
                )}

                {/* Main Outcome Summary Banner */}
                <div className={`outcome-banner outcome-${result.decision.toLowerCase()}`}>
                  <div className="outcome-left">
                    <div className="outcome-icon-wrap">
                      {result.decision === 'BLOCK' && <ShieldAlert size={32} />}
                      {result.decision === 'WARN' && <AlertTriangle size={32} />}
                      {result.decision === 'ALLOW' && <ShieldCheck size={32} />}
                    </div>
                    <div>
                      <span className="outcome-verdict-tag">DECISION VERDICT</span>
                      <h3 className="outcome-decision">{result.decision}</h3>
                      <span className="outcome-level">Risk Level: <strong>{result.risk_level}</strong></span>
                    </div>
                  </div>

                  <div className="outcome-score-box">
                    <span className="score-num">{result.risk_score}</span>
                    <span className="score-denominator">/ 100</span>
                    <span className="score-text">Risk Index</span>
                  </div>
                </div>

                {/* Warning Sandbox Actions */}
                {result.decision === 'WARN' && !isBypassed && (
                  <div className="sandbox-warning-box">
                    <div className="sandbox-title">
                      <AlertTriangle size={18} className="text-warn" />
                      <h4>Sandbox Isolation Guard Active</h4>
                    </div>
                    <p className="sandbox-desc">
                      This message contains unverified characteristics or suspicious links. Choose an administrative action:
                    </p>
                    <div className="sandbox-btn-group">
                      <button onClick={() => handleReport('bypass_report')} className="gov-btn btn-proceed">
                        Proceed with Caution
                      </button>
                      <button onClick={handleDismiss} className="gov-btn btn-dismiss">
                        Dismiss
                      </button>
                      <button onClick={() => handleReport('spam_report')} className="gov-btn btn-report">
                        Report to Telecom Registry
                      </button>
                    </div>
                    {reportStatus && <div className="sandbox-status-text">{reportStatus}</div>}
                  </div>
                )}

                {/* Bypass Notice */}
                {result.decision === 'WARN' && isBypassed && (
                  <div className="bypass-success-box">
                    <CheckCircle2 size={18} className="text-success" />
                    <span>Guard manually bypassed. Audit trail recorded in telemetry database.</span>
                  </div>
                )}

                {/* General Report Status */}
                {reportStatus && result.decision !== 'WARN' && (
                  <div className="report-success-box">
                    <CheckCircle2 size={16} className="text-success" />
                    <span>{reportStatus}</span>
                  </div>
                )}

                {/* Explanation synthesis */}
                <div className="synthesis-box">
                  <h4>Synthesis & Explanation</h4>
                  <p>{result.explanation}</p>
                </div>

                {/* Signals Table / Breakdown */}
                <div className="signals-table-wrap">
                  <h4>Analyzed Security Vectors</h4>
                  <table className="gov-table">
                    <thead>
                      <tr>
                        <th>Security Vector</th>
                        <th>Status & Evaluation</th>
                        <th>Confidence</th>
                        <th>Weight</th>
                      </tr>
                    </thead>
                    <tbody>
                      {result.signals.map((sig, idx) => (
                        <tr key={idx} className={sig.weight > 0 ? "row-threat" : ""}>
                          <td className="vector-name">
                            <div className="vector-icon-flex">
                              {getSourceIcon(sig.source)}
                              <span>{getSourceLabel(sig.source)}</span>
                            </div>
                          </td>
                          <td className="vector-desc">{sig.description}</td>
                          <td className="vector-conf">{(sig.confidence * 100).toFixed(0)}%</td>
                          <td className="vector-weight">
                            <span className={sig.weight > 0 ? "weight-badge danger" : "weight-badge clean"}>
                              {sig.weight}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>

              </div>
            )}

          </div>
        </section>

      </main>

      {/* 6. STATISTICAL SUMMARY STRIP (NSP Style Metric Counters) */}
      <section className="gov-container stats-section">
        <div className="stat-card">
          <div className="stat-num">5</div>
          <div className="stat-label">Security Analyzers Running</div>
        </div>
        <div className="stat-card">
          <div className="stat-num">&lt; 800ms</div>
          <div className="stat-label">Guaranteed SLA Latency</div>
        </div>
        <div className="stat-card">
          <div className="stat-num">TRAI DLT</div>
          <div className="stat-label">Header Validation Engine</div>
        </div>
        <div className="stat-card">
          <div className="stat-num">100%</div>
          <div className="stat-label">SSRF Interception Shield</div>
        </div>
      </section>

      {/* 7. OFFICIAL GOVERNMENT FOOTER (NSP Blue Multi-Column Footer) */}
      <footer className="gov-footer">
        <div className="gov-container footer-layout">
          <div className="footer-col">
            <h4>About Sanchar Saathi</h4>
            <p>
              An initiative of the Department of Telecommunications to empower citizens and proactively detect phishing, SMS fraud, and cyber security threats.
            </p>
          </div>
          <div className="footer-col">
            <h4>Quick Links</h4>
            <ul className="footer-links">
              <li><a href="#privacy">Website Policies</a></li>
              <li><a href="#terms">Terms of Service</a></li>
              <li><a href="#dlt">DLT Registry Guidelines</a></li>
              <li><a href="#cybercrime">National Cybercrime Reporting Portal (1930)</a></li>
            </ul>
          </div>
        </div>

        <div className="footer-bottom-bar">
          <div className="gov-container bottom-bar-inner">
            <span>&copy; 2026 Department of Telecommunications, Government of India. All Rights Reserved.</span>
            <span>Designed adhering to Guidelines for Indian Government Websites (GIGW).</span>
          </div>
        </div>
      </footer>

    </div>
  );
}
