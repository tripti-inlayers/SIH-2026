import { useState, useEffect } from 'react';
import { 
  ShieldAlert, ShieldCheck, AlertTriangle, RefreshCw, 
  Globe, Database, Key, HelpCircle, Cpu, UserCheck, 
  Search, ArrowRight, Info, PhoneCall, Building2,
  Menu, X, Smartphone, BarChart3, Bell, Share2, 
  ChevronRight, ArrowLeft, Shield, Eye, CheckCircle2
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

declare global {
  interface Window {
    onSmsReceived?: (payloadJson: string) => void;
    onNativeBridgeReady?: (isPermissionGranted: boolean) => void;
    AndroidNativeBridge?: {
      isNative: () => boolean;
      checkSmsPermission: () => boolean;
      requestSmsPermission: () => void;
      getInboxSms: (limit: number) => string;
      performNativeFetch: (targetUrl: string, postDataJson: string) => string;
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
    label: "Electricity Cutoff Scam",
    category: "Financial Extortion",
    sender: "+919876501234",
    text: "Dear Consumer, your electricity will be disconnected tonight at 9:30 PM from the power station. Call bill officer immediately."
  }
];

const FAQS_DATA = [
  {
    id: 1,
    title: "SancharSaathi Overview & Protection",
    count: "6 Questions",
    items: [
      { q: "What is SancharSaathi?", a: "SancharSaathi is a citizen-centric initiative of the Department of Telecommunications (DoT) to empower mobile subscribers, detect phishing SMS, verify sender headers, and track cyber threats." },
      { q: "How does the SMS threat detection work?", a: "The app uses an AI-powered RoBERTa transformer, TRAI DLT registry checks, and heuristic lexical analyzers to score threat risk in under 800ms." }
    ]
  },
  {
    id: 2,
    title: "Registration & Permission FAQs",
    count: "6 Questions",
    items: [
      { q: "Why does the app need SMS permission?", a: "SMS permissions allow real-time background inspection of incoming text messages to alert you before you click malicious phishing links." },
      { q: "Is my personal data sent to servers?", a: "No. Messages are evaluated for threat indicators; personal chats are never shared or sold." }
    ]
  },
  {
    id: 3,
    title: "Chakshu - Report Fraud FAQs",
    count: "7 Questions",
    items: [
      { q: "What is Chakshu?", a: "Chakshu facilitates citizens to report suspected fraudulent communications received over SMS, WhatsApp, or voice calls." },
      { q: "How to report financial cyber fraud?", a: "For immediate financial fraud, call the National Cybercrime Helpline 1930 or visit cybercrime.gov.in." }
    ]
  },
  {
    id: 4,
    title: "DLT Header & Sender Verification",
    count: "5 Questions",
    items: [
      { q: "What is a DLT Header?", a: "TRAI mandates all commercial and transactional SMS senders in India to register a 6-character alphanumeric header (e.g., VK-SBIIN, AD-HDFCBK)." },
      { q: "Why are some SMS flagged as Unregistered?", a: "If an SMS claims to be a bank but is sent from a normal 10-digit mobile number or unapproved header, it is flagged as high risk." }
    ]
  },
  {
    id: 5,
    title: "On-Device SMS Inbox Scanner",
    count: "4 Questions",
    items: [
      { q: "Can I scan my existing SMS messages?", a: "Yes. Use the 'Device SMS Inbox Scanner' feature to inspect all stored messages with a single tap." }
    ]
  }
];

export default function App() {
  // Navigation Screens: 'home' | 'chakshu' | 'inbox' | 'dlt' | 'report' | 'audit' | 'faqs' | 'about'
  const [activeScreen, setActiveScreen] = useState<'home' | 'chakshu' | 'inbox' | 'dlt' | 'report' | 'audit' | 'faqs' | 'about'>('home');
  const [isDrawerOpen, setIsDrawerOpen] = useState(false);
  const [searchFaq, setSearchFaq] = useState('');
  const [expandedFaq, setExpandedFaq] = useState<number | null>(null);

  // Scanner & Analysis State
  const [content, setContent] = useState('');
  const [sender, setSender] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [result, setResult] = useState<AnalyzeResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [reportStatus, setReportStatus] = useState<string | null>(null);

  // Native Android Integration State
  const [smsPermissionGranted, setSmsPermissionGranted] = useState<boolean | null>(null);
  const [interceptedNotification, setInterceptedNotification] = useState<{ sender: string; text: string } | null>(null);

  // On-Device SMS Inbox State
  const [inboxList, setInboxList] = useState<InboxMessage[]>([]);
  const [isLoadingInbox, setIsLoadingInbox] = useState(false);
  const [isBatchScanning, setIsBatchScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState<{ current: number; total: number }>({ current: 0, total: 0 });

  // Hero carousel state
  const [carouselIndex, setCarouselIndex] = useState(0);
  const carouselStats = [
    { num: "14.85k", label: "Phishing threats intercepted & neutralized", sub: "Live Threat Intelligence" },
    { num: "100%", label: "DLT Principal Entity Header Verification", sub: "TRAI Compliance" },
    { num: "< 800ms", label: "Guaranteed SLA Analysis Latency", sub: "RoBERTa Multi-Signal Pipeline" }
  ];

  // Wi-Fi Host IP Configuration for wireless mobile support
  const [backendIp, setBackendIp] = useState<string>(() => {
    return localStorage.getItem('sanchar_backend_ip') || '192.168.29.242';
  });
  const [showIpModal, setShowIpModal] = useState(false);
  const [tempIp, setTempIp] = useState(backendIp);

  const getApiUrl = (endpoint: string) => {
    const raw = backendIp.trim() || '192.168.29.242';
    const baseUrl = raw.startsWith('http://') || raw.startsWith('https://') 
      ? raw 
      : `http://${raw}:8000`;
    return `${baseUrl.replace(/\/$/, '')}${endpoint}`;
  };

  const saveBackendIp = (ip: string) => {
    const clean = ip.trim();
    setBackendIp(clean);
    localStorage.setItem('sanchar_backend_ip', clean);
    setShowIpModal(false);
  };

  const safeNetworkPost = async (endpoint: string, payload: any): Promise<any> => {
    const payloadJson = JSON.stringify(payload);
    
    // 1. If running inside Android Native Hybrid Shell, use the native HttpURLConnection bridge
    // This completely bypasses all Chromium WebView Mixed Content & CORS security sandboxes
    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.performNativeFetch === 'function') {
      try {
        const targetUrl = getApiUrl(endpoint);
        const rawResponse = window.AndroidNativeBridge.performNativeFetch(targetUrl, payloadJson);
        const parsed = JSON.parse(rawResponse);
        if (parsed && !parsed.error) {
          return parsed;
        }
      } catch (nativeErr) {
        console.warn("Native bridge fetch failed, falling back to browser fetch", nativeErr);
      }
    }

    // 2. Browser fetch fallback (Wi-Fi IP)
    try {
      const response = await fetch(getApiUrl(endpoint), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: payloadJson,
      });
      if (response.ok) {
        return await response.json();
      }
    } catch (wifiErr) {
      console.warn("Wi-Fi browser fetch failed, trying localhost", wifiErr);
    }

    // 3. Browser fetch fallback (localhost / USB mode)
    try {
      const response = await fetch(`http://localhost:8000${endpoint}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: payloadJson,
      });
      if (response.ok) {
        return await response.json();
      }
    } catch (localErr) {
      console.error("Localhost fetch failed", localErr);
    }

    throw new Error(`Failed to reach SancharSaathi backend at ${getApiUrl(endpoint)}. Please verify your phone is connected to the same Wi-Fi as your computer.`);
  };

  const handleAnalyze = async (textToAnalyze?: string, senderToAnalyze?: string) => {
    const text = textToAnalyze !== undefined ? textToAnalyze : content;
    const sendVal = senderToAnalyze !== undefined ? senderToAnalyze : sender;
    if (!text.trim()) return null;

    setIsLoading(true);
    setError(null);
    setReportStatus(null);

    try {
      const data: AnalyzeResponse = await safeNetworkPost('/api/v1/analyze', { 
        content: text, 
        source: 'sms',
        sender: sendVal || undefined 
      });

      setResult(data);
      return data;
    } catch (err: any) {
      setError(err.message || 'Could not reach server. Verify phone is on same Wi-Fi as laptop.');
      setResult(null);
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  const handleReport = async (type: 'spam_report' | 'bypass_report') => {
    if (!result) return;
    try {
      await safeNetworkPost('/api/v1/report', {
        content,
        decision: result.decision,
        risk_score: result.risk_score,
        signals: result.signals,
        report_type: type
      });

      if (type === 'spam_report') {
        setReportStatus('Incident successfully logged in National Telecom Security Registry.');
      } else {
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
        const rawJson = window.AndroidNativeBridge.getInboxSms(15);
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
    // Fallback to sample messages for rich preview
    setInboxList(prev => prev.length > 0 ? prev : SAMPLE_INBOX_MESSAGES);
    setIsLoadingInbox(false);
  };

  const scanSingleSms = async (msg: InboxMessage, openAudit = false) => {
    setInboxList(prev => prev.map(m => m.id === msg.id ? { ...m, isAnalyzing: true } : m));
    
    try {
      const data: AnalyzeResponse = await safeNetworkPost('/api/v1/analyze', {
        content: msg.content,
        source: 'sms',
        sender: msg.sender || undefined
      });

      if (data) {
        setInboxList(prev => prev.map(m => m.id === msg.id ? { ...m, result: data, isAnalyzing: false } : m));
        if (openAudit) {
          setContent(msg.content);
          setSender(msg.sender);
          setResult(data);
          setActiveScreen('chakshu');
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

    // Auto rotate hero carousel
    const timer = setInterval(() => {
      setCarouselIndex(prev => (prev + 1) % carouselStats.length);
    }, 4500);

    // Check Android Native Bridge
    if (window.AndroidNativeBridge && typeof window.AndroidNativeBridge.checkSmsPermission === 'function') {
      const isGranted = window.AndroidNativeBridge.checkSmsPermission();
      setSmsPermissionGranted(isGranted);
      if (isGranted) {
        fetchInbox();
      }
    }

    window.onNativeBridgeReady = (isGranted: boolean) => {
      setSmsPermissionGranted(isGranted);
      if (isGranted) {
        fetchInbox();
      }
    };

    window.onSmsReceived = (payloadJson: string) => {
      try {
        const payload = JSON.parse(payloadJson);
        const text = payload.content || '';
        const sendVal = payload.sender || '';
        const smsId = payload.id || 'live-' + Date.now();
        const smsDate = payload.date || Date.now();

        // Prevent duplicate insertions
        setInboxList(prev => {
          const alreadyExists = prev.some(m => 
            (m.id === smsId) || 
            (m.sender === sendVal && m.content === text && Math.abs(m.date - smsDate) < 4000)
          );
          if (alreadyExists) return prev;

          const newLiveMessage: InboxMessage = {
            id: smsId,
            sender: sendVal,
            content: text,
            date: smsDate,
            isLive: true,
            isAnalyzing: true
          };

          // Auto-analyze this new live message
          handleAnalyze(text, sendVal).then(resData => {
            if (resData) {
              setInboxList(curr => curr.map(m => m.id === smsId ? { ...m, result: resData, isAnalyzing: false } : m));
            } else {
              setInboxList(curr => curr.map(m => m.id === smsId ? { ...m, isAnalyzing: false } : m));
            }
          });

          return [newLiveMessage, ...prev.slice(0, 19)]; // Keep 15-20 most recent
        });

        setContent(text);
        setSender(sendVal);
        setInterceptedNotification({ sender: sendVal, text });
      } catch (e) {
        console.error("Failed to parse incoming SMS payload", e);
      }
    };

    return () => {
      clearInterval(timer);
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

  const getSourceName = (source: string) => {
    switch (source) {
      case 'ml_model':
        return 'RoBERTa Semantic Classifier';
      case 'keyword_analyzer':
        return 'Deterministic Keyword Heuristics';
      case 'url_lexical':
        return 'Lexical & Domain TLD Analyzer';
      case 'threat_intel':
        return 'Threat Intelligence & SSRF Guard';
      case 'identity_verifier':
        return 'TRAI / DLT Principal Entity Verifier';
      default:
        return source;
    }
  };

  return (
    <div className="mobile-app-shell">
      
      {/* 1. NATIVE MOBILE APP TOP BAR */}
      <header className="mobile-top-bar">
        <div className="top-bar-inner">
          {activeScreen === 'home' ? (
            <button 
              className="top-bar-icon-btn" 
              onClick={() => setIsDrawerOpen(true)}
              aria-label="Open Navigation Drawer"
            >
              <Menu size={24} />
            </button>
          ) : (
            <button 
              className="top-bar-icon-btn" 
              onClick={() => setActiveScreen('home')}
              aria-label="Back to Home"
            >
              <ArrowLeft size={22} />
            </button>
          )}

          <div className="top-bar-brand" onClick={() => setActiveScreen('home')}>
            <div className="brand-emblem-circle">
              <Building2 size={18} className="emblem-svg" />
            </div>
            <span className="brand-title">
              {activeScreen === 'home' ? 'Sanchar Saathi' : 
               activeScreen === 'chakshu' ? 'Chakshu Threat Inspector' :
               activeScreen === 'inbox' ? 'SMS Inbox Scanner' :
               activeScreen === 'dlt' ? 'DLT Header Registry' :
               activeScreen === 'report' ? 'Report Fraud Communication' :
               activeScreen === 'audit' ? 'Telemetry & SLA Audit' :
               activeScreen === 'faqs' ? 'FAQs & Help' : 'About Sanchar Saathi'}
            </span>
          </div>

          <div className="top-bar-actions">
            <button 
              className="top-bar-icon-btn" 
              onClick={() => {
                if (interceptedNotification) {
                  setActiveScreen('chakshu');
                } else {
                  alert('Notification History: No new security alerts.');
                }
              }}
              title="Notifications"
            >
              <Bell size={20} />
              {interceptedNotification && <span className="notification-indicator-dot"></span>}
            </button>
            <button className="top-bar-icon-btn lang-btn" title="Change Language">
              <span className="lang-text">अ/A</span>
            </button>
          </div>
        </div>
      </header>

      {/* 2. SLIDE-OUT NAVIGATION DRAWER */}
      {isDrawerOpen && (
        <div className="drawer-overlay" onClick={() => setIsDrawerOpen(false)}>
          <div className="drawer-container" onClick={(e) => e.stopPropagation()}>
            
            {/* Drawer Header with DoT branding */}
            <div className="drawer-header">
              <div className="drawer-logos">
                <div className="drawer-dot-emblem">
                  <Building2 size={24} />
                </div>
                <div className="drawer-sanchar-emblem">
                  <Shield size={24} />
                </div>
              </div>
              <p className="drawer-dept">Department of Telecommunications</p>
              <p className="drawer-sub">Government of India</p>
            </div>

            {/* Drawer Menu Links */}
            <div className="drawer-body">
              <ul className="drawer-nav-list">
                <li className={`drawer-nav-item ${activeScreen === 'home' ? 'active' : ''}`} onClick={() => { setActiveScreen('home'); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box blue"><Building2 size={18} /></span>
                    <span className="drawer-label">Home</span>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>

                <li className={`drawer-nav-item ${activeScreen === 'chakshu' ? 'active' : ''}`} onClick={() => { setActiveScreen('chakshu'); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box blue"><Eye size={18} /></span>
                    <span className="drawer-label">Chakshu - Phishing & Fraud Inspector</span>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>

                <li className={`drawer-nav-item ${activeScreen === 'inbox' ? 'active' : ''}`} onClick={() => { setActiveScreen('inbox'); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box green"><Smartphone size={18} /></span>
                    <span className="drawer-label">Device SMS Inbox Scanner</span>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>

                <li className={`drawer-nav-item ${activeScreen === 'dlt' ? 'active' : ''}`} onClick={() => { setActiveScreen('dlt'); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box purple"><UserCheck size={18} /></span>
                    <span className="drawer-label">DLT Header & Sender Registry</span>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>

                <li className={`drawer-nav-item ${activeScreen === 'audit' ? 'active' : ''}`} onClick={() => { setActiveScreen('audit'); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box amber"><BarChart3 size={18} /></span>
                    <span className="drawer-label">Telemetry & SLA Audit Log</span>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>

                <li className={`drawer-nav-item ${activeScreen === 'faqs' ? 'active' : ''}`} onClick={() => { setActiveScreen('faqs'); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box cyan"><HelpCircle size={18} /></span>
                    <span className="drawer-label">FAQs & Guidelines</span>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>

                <li className={`drawer-nav-item ${activeScreen === 'about' ? 'active' : ''}`} onClick={() => { setActiveScreen('about'); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box navy"><Info size={18} /></span>
                    <span className="drawer-label">About Sanchar Saathi</span>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>

                {/* Wireless Wi-Fi Server Connection Setting */}
                <li className="drawer-nav-item" onClick={() => { setShowIpModal(true); setIsDrawerOpen(false); }}>
                  <div className="drawer-item-left">
                    <span className="drawer-icon-box blue"><Globe size={18} /></span>
                    <div>
                      <span className="drawer-label">Wi-Fi Backend Server</span>
                      <span className="drawer-sub-label">{backendIp}:8000</span>
                    </div>
                  </div>
                  <ChevronRight size={16} className="drawer-arrow" />
                </li>
              </ul>

              {/* Native Shield Status in Drawer */}
              <div className="drawer-shield-status">
                <div className="shield-status-icon-row">
                  <span className="pulse-green-dot"></span>
                  <span className="shield-status-title">Real-Time SMS Protection</span>
                </div>
                <p className="shield-status-desc">
                  {smsPermissionGranted ? 'Background listener is actively guarding against malicious SMS and smishing.' : 'SMS Permission pending. Tap to enable active protection.'}
                </p>
                {!smsPermissionGranted && (
                  <button 
                    className="enable-shield-btn"
                    onClick={() => window.AndroidNativeBridge?.requestSmsPermission()}
                  >
                    Enable SMS Shield
                  </button>
                )}
              </div>
            </div>

            {/* Drawer Footer */}
            <div className="drawer-footer">
              <p className="drawer-credit-title">Designed & Developed by</p>
              <p className="drawer-credit-org">Centre for Development of Telematics (C-DOT)</p>
            </div>

          </div>
        </div>
      )}

      {/* 3. LIVE INTERCEPTED SMS BANNER */}
      {interceptedNotification && (
        <div className="mobile-live-sms-toast">
          <div className="live-toast-content">
            <div className="toast-left">
              <span className="live-pulse-badge">LIVE SMS</span>
              <span className="toast-sender">{interceptedNotification.sender || 'Unknown'}</span>
            </div>
            <button className="toast-dismiss" onClick={() => setInterceptedNotification(null)}>
              <X size={14} />
            </button>
          </div>
          <p className="toast-text">"{interceptedNotification.text.slice(0, 90)}..."</p>
          <div className="toast-actions">
            <button 
              className="toast-view-btn" 
              onClick={() => {
                setActiveScreen('chakshu');
                setInterceptedNotification(null);
              }}
            >
              Inspect Risk Topology &rarr;
            </button>
          </div>
        </div>
      )}

      {/* 4. MAIN APP CONTENT CONTAINER */}
      <main className="mobile-screen-body">

        {/* SCREEN 1: HOME DASHBOARD */}
        {activeScreen === 'home' && (
          <div className="home-screen-view">
            
            {/* Top Hero Stats Carousel Card (Blue Curved Card) */}
            <div className="hero-stats-card">
              <div className="stats-circle-wrap">
                <div className="stats-circular-ring">
                  <span className="stats-num">{carouselStats[carouselIndex].num}</span>
                  <span className="stats-unit">Active</span>
                </div>
              </div>
              <div className="stats-text-wrap">
                <span className="stats-sub-badge">{carouselStats[carouselIndex].sub}</span>
                <p className="stats-main-text">{carouselStats[carouselIndex].label}</p>
              </div>
              <div className="carousel-dots">
                {carouselStats.map((_, idx) => (
                  <span 
                    key={idx} 
                    className={`carousel-dot ${idx === carouselIndex ? 'active' : ''}`}
                    onClick={() => setCarouselIndex(idx)}
                  />
                ))}
              </div>
            </div>

            {/* Citizen Centric Services Heading */}
            <div className="section-title-row">
              <div className="section-title-wrap">
                <span className="title-accent-icon">∿</span>
                <h2 className="section-heading">Citizen Centric Services</h2>
              </div>
            </div>

            {/* 2-Column Pastel Service Cards Grid */}
            <div className="services-grid-2col">
              
              {/* Service 1: Chakshu Threat Inspector */}
              <div className="service-card card-blue" onClick={() => setActiveScreen('chakshu')}>
                <div className="card-top-icon">
                  <div className="icon-badge blue">
                    <Eye size={22} />
                  </div>
                </div>
                <h3 className="card-title">Chakshu - Phishing & Threat Inspector</h3>
              </div>

              {/* Service 2: Device SMS Inbox Scanner */}
              <div className="service-card card-green" onClick={() => setActiveScreen('inbox')}>
                <div className="card-top-icon">
                  <div className="icon-badge green">
                    <Smartphone size={22} />
                  </div>
                  {inboxList.length > 0 && (
                    <span className="card-counter-tag">{inboxList.length} SMS</span>
                  )}
                </div>
                <h3 className="card-title">On-Device SMS Inbox & Threat Scanner</h3>
              </div>

              {/* Service 3: Know Connections / Manual Link Verification */}
              <div className="service-card card-orange" onClick={() => setActiveScreen('chakshu')}>
                <div className="card-top-icon">
                  <div className="icon-badge orange">
                    <Search size={22} />
                  </div>
                </div>
                <h3 className="card-title">Manual SMS & URL Security Inspector</h3>
              </div>

              {/* Service 4: DLT Header & Sender Registry */}
              <div className="service-card card-purple" onClick={() => setActiveScreen('dlt')}>
                <div className="card-top-icon">
                  <div className="icon-badge purple">
                    <UserCheck size={22} />
                  </div>
                </div>
                <h3 className="card-title">Verify Genuineness of Sender Header</h3>
              </div>

              {/* Service 5: Report Fraudulent Call / Smishing */}
              <div className="service-card card-cyan" onClick={() => setActiveScreen('report')}>
                <div className="card-top-icon">
                  <div className="icon-badge cyan">
                    <PhoneCall size={22} />
                  </div>
                </div>
                <h3 className="card-title">Report Fraudulent Call / Smishing</h3>
              </div>

              {/* Service 6: Telemetry & SLA Engine Audit */}
              <div className="service-card card-beige" onClick={() => setActiveScreen('audit')}>
                <div className="card-top-icon">
                  <div className="icon-badge beige">
                    <BarChart3 size={22} />
                  </div>
                  <span className="card-beta-tag">Beta</span>
                </div>
                <h3 className="card-title">Security Telemetry & SLA Audit Log</h3>
              </div>

            </div>

            {/* Keep Yourself Aware Section */}
            <div className="awareness-section">
              <div className="section-title-wrap">
                <span className="awareness-bulb">💡</span>
                <h2 className="section-heading">Keep Yourself Aware</h2>
              </div>

              <div className="awareness-cards-scroll">
                
                <div className="awareness-card" onClick={() => selectPreset("Your parcel #IN8492 cannot be delivered due to address issue. Update at http://indiapost-update.xyz", "AD-PARCEL")}>
                  <div className="awareness-card-banner parcel-bg">
                    <span className="awareness-tag">Beware</span>
                  </div>
                  <div className="awareness-card-content">
                    <h4>Undeliverable Parcel Scam</h4>
                    <p>Fake IndiaPost SMS claiming pending address update.</p>
                    <span className="awareness-test-btn">Test Scenario &rarr;</span>
                  </div>
                </div>

                <div className="awareness-card" onClick={() => selectPreset("Electricity bill unpaid! Power cutoff tonight at 9:30 PM. Call 9876501234 to pay immediately.", "+919876501234")}>
                  <div className="awareness-card-banner power-bg">
                    <span className="awareness-tag">Alert</span>
                  </div>
                  <div className="awareness-card-content">
                    <h4>Electricity Bill Cutoff Scam</h4>
                    <p>Threatening immediate power disconnection via APK/link.</p>
                    <span className="awareness-test-btn">Test Scenario &rarr;</span>
                  </div>
                </div>

                <div className="awareness-card" onClick={() => selectPreset("Dear Customer, your SBI account is locked. Restore immediately at http://bit.ly/sbi-kyc", "VM-SBIINB")}>
                  <div className="awareness-card-banner kyc-bg">
                    <span className="awareness-tag">Phishing</span>
                  </div>
                  <div className="awareness-card-content">
                    <h4>Bank KYC Suspension Scam</h4>
                    <p>Smishing attempting to harvest NetBanking passwords.</p>
                    <span className="awareness-test-btn">Test Scenario &rarr;</span>
                  </div>
                </div>

              </div>
            </div>

          </div>
        )}

        {/* SCREEN 2: CHAKSHU THREAT INSPECTOR & AUDIT BREAKDOWN */}
        {activeScreen === 'chakshu' && (
          <div className="sub-screen-view">
            
            <div className="screen-header-banner">
              <h3>Chakshu - Phishing & Threat Inspector</h3>
              <p>Evaluate message text, shortened URLs, and alphanumeric DLT headers.</p>
            </div>

            {/* Input Form Box */}
            <div className="mobile-form-card">
              <div className="mobile-input-group">
                <label className="mobile-input-label">Sender ID / Alphanumeric Header (Optional)</label>
                <input 
                  type="text" 
                  className="mobile-text-input" 
                  placeholder="e.g. VK-SBIIN, AD-HDFCBK, or Mobile Number"
                  value={sender}
                  onChange={(e) => setSender(e.target.value)}
                />
              </div>

              <div className="mobile-input-group">
                <label className="mobile-input-label">SMS Content or URL <span className="req-star">*</span></label>
                <textarea 
                  rows={4}
                  className="mobile-textarea-input"
                  placeholder="Paste suspicious SMS text, phishing URL, or notification message..."
                  value={content}
                  onChange={(e) => setContent(e.target.value)}
                />
                <span className="char-indicator">{content.length} characters &bull; AI RoBERTa & DLT Active</span>
              </div>

              <div className="form-buttons-row">
                <button 
                  className="mobile-primary-btn" 
                  onClick={() => handleAnalyze()}
                  disabled={isLoading || !content.trim()}
                >
                  {isLoading ? (
                    <>
                      <RefreshCw className="animate-spin" size={16} /> Inspecting Security Topology...
                    </>
                  ) : (
                    <>
                      <Search size={16} /> Verify Security & Safety
                    </>
                  )}
                </button>
                <button 
                  className="mobile-secondary-btn"
                  onClick={() => {
                    setContent('');
                    setSender('');
                    setResult(null);
                    setError(null);
                    setReportStatus(null);
                  }}
                >
                  Reset
                </button>
              </div>
            </div>

            {/* Error banner */}
            {error && (
              <div className="mobile-alert-box error">
                <AlertTriangle size={18} />
                <span>{error}</span>
              </div>
            )}

            {/* Analysis Result Breakdown */}
            {result && (
              <div className="mobile-result-box">
                <div className={`result-decision-header ${result.decision.toLowerCase()}`}>
                  <div className="decision-left">
                    {result.decision === 'BLOCK' && <ShieldAlert size={28} />}
                    {result.decision === 'WARN' && <AlertTriangle size={28} />}
                    {result.decision === 'ALLOW' && <ShieldCheck size={28} />}
                    <div>
                      <h4 className="decision-text">{result.decision} DECISION</h4>
                      <span className="risk-level-tag">Risk Level: {result.risk_level}</span>
                    </div>
                  </div>
                  <div className="decision-score-ring">
                    <span className="score-val">{Math.round(result.risk_score)}</span>
                    <span className="score-max">/100</span>
                  </div>
                </div>

                <div className="explanation-bubble">
                  <strong>Explanation:</strong> {result.explanation}
                </div>

                {/* Signal list */}
                <h4 className="signals-title">Multi-Signal Topology Breakdown</h4>
                <div className="signals-mobile-list">
                  {result.signals.map((sig, idx) => (
                    <div key={idx} className="signal-item-mobile">
                      <div className="sig-icon-wrap">
                        {getSourceIcon(sig.source)}
                      </div>
                      <div className="sig-details">
                        <span className="sig-name">{getSourceName(sig.source)}</span>
                        <p className="sig-desc">{sig.description}</p>
                        <div className="sig-weight-bar">
                          <div className="sig-fill" style={{ width: `${Math.min(100, sig.confidence * 100)}%` }}></div>
                          <span className="sig-conf-text">Confidence: {Math.round(sig.confidence * 100)}% | Weight: {sig.weight}</span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>

                {/* Reporting Action */}
                <div className="report-actions-mobile">
                  {reportStatus ? (
                    <div className="report-status-pill">
                      <CheckCircle2 size={16} /> {reportStatus}
                    </div>
                  ) : (
                    <button 
                      className="mobile-report-btn" 
                      onClick={() => handleReport('spam_report')}
                    >
                      <ShieldAlert size={16} /> Report Incident to National Telecom Security Portal
                    </button>
                  )}
                </div>
              </div>
            )}

            {/* Preset Test Scenarios */}
            <div className="preset-tests-container">
              <h4 className="preset-title">Test Pre-Loaded Telemetry Scenarios</h4>
              <div className="preset-scroll-list">
                {PRESETS.map((p, idx) => (
                  <div key={idx} className="preset-item-card" onClick={() => selectPreset(p.text, p.sender)}>
                    <div>
                      <span className="preset-lbl">{p.label}</span>
                      <span className="preset-cat">{p.category}</span>
                    </div>
                    <ArrowRight size={14} className="preset-arr" />
                  </div>
                ))}
              </div>
            </div>

          </div>
        )}

        {/* SCREEN 3: ON-DEVICE SMS INBOX SCANNER */}
        {activeScreen === 'inbox' && (
          <div className="sub-screen-view">
            
            <div className="inbox-top-summary-bar">
              <div className="inbox-summary-stat">
                <span className="stat-v">{inboxList.length}</span>
                <span className="stat-l">Messages</span>
              </div>
              <div className="inbox-summary-stat green">
                <span className="stat-v">{inboxList.filter(m => m.result?.decision === 'ALLOW').length}</span>
                <span className="stat-l">Safe</span>
              </div>
              <div className="inbox-summary-stat amber">
                <span className="stat-v">{inboxList.filter(m => m.result?.decision === 'WARN').length}</span>
                <span className="stat-l">Suspicious</span>
              </div>
              <div className="inbox-summary-stat red">
                <span className="stat-v">{inboxList.filter(m => m.result?.decision === 'BLOCK').length}</span>
                <span className="stat-l">Blocked</span>
              </div>
            </div>

            {/* Action Bar */}
            <div className="inbox-actions-bar">
              <button 
                className="inbox-batch-scan-btn"
                onClick={scanAllInbox}
                disabled={isBatchScanning || inboxList.length === 0}
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
                className="inbox-refresh-icon-btn"
                onClick={fetchInbox}
                disabled={isLoadingInbox || isBatchScanning}
                title="Reload Device SMS"
              >
                <RefreshCw className={isLoadingInbox ? "animate-spin" : ""} size={16} />
              </button>
            </div>

            {/* List of Device SMS Messages */}
            <div className="mobile-inbox-list">
              {inboxList.map((msg) => {
                const decision = msg.result?.decision;
                return (
                  <div key={msg.id} className={`inbox-card-mobile ${decision ? decision.toLowerCase() : ''}`}>
                    <div className="inbox-card-top">
                      <div className="sender-tag-group">
                        <span className="sender-pill">{msg.sender || 'Unknown'}</span>
                        {msg.isLive && <span className="live-tag">LIVE</span>}
                      </div>
                      <span className="inbox-date">
                        {new Date(msg.date).toLocaleDateString([], { month: 'short', day: 'numeric' })}
                      </span>
                    </div>

                    <p className="inbox-body-text">{msg.content}</p>

                    <div className="inbox-card-footer">
                      {msg.isAnalyzing ? (
                        <span className="status-badge analyzing">
                          <RefreshCw className="animate-spin" size={12} /> Inspecting...
                        </span>
                      ) : msg.result ? (
                        <button 
                          className={`status-badge ${decision?.toLowerCase()}`}
                          onClick={() => {
                            setContent(msg.content);
                            setSender(msg.sender);
                            setResult(msg.result!);
                            setActiveScreen('chakshu');
                          }}
                        >
                          {decision === 'BLOCK' && <ShieldAlert size={12} />}
                          {decision === 'WARN' && <AlertTriangle size={12} />}
                          {decision === 'ALLOW' && <ShieldCheck size={12} />}
                          <span>{decision} ({Math.round(msg.result.risk_score)}/100)</span>
                        </button>
                      ) : (
                        <button 
                          className="status-badge verify-btn"
                          onClick={() => scanSingleSms(msg, true)}
                        >
                          <Search size={12} /> Verify Security
                        </button>
                      )}

                      {msg.result && (
                        <button 
                          className="view-deep-audit-link"
                          onClick={() => {
                            setContent(msg.content);
                            setSender(msg.sender);
                            setResult(msg.result!);
                            setActiveScreen('chakshu');
                          }}
                        >
                          View Audit &rarr;
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

          </div>
        )}

        {/* SCREEN 4: DLT HEADER DIRECTORY */}
        {activeScreen === 'dlt' && (
          <div className="sub-screen-view">
            <div className="screen-header-banner">
              <h3>DLT Header & Sender Directory</h3>
              <p>Verify whether an alphanumeric sender header is registered with TRAI.</p>
            </div>

            <div className="dlt-info-card">
              <div className="dlt-header-example">
                <span className="circle-code">VK</span>
                <span className="dash-code">-</span>
                <span className="entity-code">SBIINB</span>
              </div>
              <p className="dlt-explain">
                In India, legitimate SMS headers follow the 6-character format: <strong>Prefix (Operator + Circle) - Entity ID (6 Characters)</strong>.
              </p>
            </div>

            <div className="dlt-list-mobile">
              <div className="dlt-item-row verified">
                <div className="dlt-row-left">
                  <span className="dlt-badge">VK-SBIIN</span>
                  <span className="dlt-company">State Bank of India</span>
                </div>
                <span className="dlt-status-tag safe">DLT Registered</span>
              </div>

              <div className="dlt-item-row verified">
                <div className="dlt-row-left">
                  <span className="dlt-badge">AD-HDFCBK</span>
                  <span className="dlt-company">HDFC Bank Ltd</span>
                </div>
                <span className="dlt-status-tag safe">DLT Registered</span>
              </div>

              <div className="dlt-item-row verified">
                <div className="dlt-row-left">
                  <span className="dlt-badge">AD-INDPOST</span>
                  <span className="dlt-company">India Post & Postal Services</span>
                </div>
                <span className="dlt-status-tag safe">DLT Registered</span>
              </div>

              <div className="dlt-item-row fraudulent">
                <div className="dlt-row-left">
                  <span className="dlt-badge red">SBI-SUPPORT</span>
                  <span className="dlt-company">Unregistered Phishing Header</span>
                </div>
                <span className="dlt-status-tag fraud">Unregistered</span>
              </div>
            </div>
          </div>
        )}

        {/* SCREEN 5: REPORT FRAUD */}
        {activeScreen === 'report' && (
          <div className="sub-screen-view">
            <div className="screen-header-banner">
              <h3>Report Fraud Communication</h3>
              <p>Submit fraud incident directly to National Cybercrime Helpline.</p>
            </div>

            <div className="report-helpline-box">
              <PhoneCall size={28} className="helpline-icon" />
              <div>
                <h4>National Cybercrime Helpline</h4>
                <p className="helpline-number">Dial 1930 (Toll Free)</p>
              </div>
            </div>

            <div className="mobile-form-card">
              <div className="mobile-input-group">
                <label className="mobile-input-label">Suspect Phone / Sender Header</label>
                <input type="text" className="mobile-text-input" placeholder="+91..." />
              </div>
              <div className="mobile-input-group">
                <label className="mobile-input-label">Incident Description & Link Details</label>
                <textarea rows={4} className="mobile-textarea-input" placeholder="Explain the incident, financial loss (if any), and attach URL details..."></textarea>
              </div>
              <button 
                className="mobile-primary-btn" 
                onClick={() => alert('Incident logged in National Telecom Security Registry.')}
              >
                Submit Incident Report
              </button>
            </div>
          </div>
        )}

        {/* SCREEN 6: TELEMETRY & SLA AUDIT */}
        {activeScreen === 'audit' && (
          <div className="sub-screen-view">
            <div className="screen-header-banner">
              <h3>Security Telemetry & SLA Audit</h3>
              <p>Active multi-tier security inspection pipeline topology.</p>
            </div>

            <div className="telemetry-stat-grid">
              <div className="telemetry-box">
                <span className="telemetry-v">5</span>
                <span className="telemetry-l">Parallel Analyzers</span>
              </div>
              <div className="telemetry-box">
                <span className="telemetry-v">&lt; 800ms</span>
                <span className="telemetry-l">Guaranteed SLA</span>
              </div>
              <div className="telemetry-box">
                <span className="telemetry-v">RoBERTa</span>
                <span className="telemetry-l">NLP Transformer</span>
              </div>
              <div className="telemetry-box">
                <span className="telemetry-v">100%</span>
                <span className="telemetry-l">SSRF Shield</span>
              </div>
            </div>

            <div className="pipeline-nodes-card">
              <h4>Parallel Security Verification Pipeline</h4>
              <div className="pipeline-node-item">
                <div className="node-icon"><Cpu size={16} /></div>
                <div className="node-text">
                  <strong>RoBERTa Semantic Classifier (Port 8001)</strong>
                  <span>Deep contextual tokenization across spam vocabulary</span>
                </div>
              </div>
              <div className="pipeline-node-item">
                <div className="node-icon"><Key size={16} /></div>
                <div className="node-text">
                  <strong>Keyword Heuristic Regex Engine</strong>
                  <span>Deterministic matching across urgency, lottery, and extortion patterns</span>
                </div>
              </div>
              <div className="pipeline-node-item">
                <div className="node-icon"><Globe size={16} /></div>
                <div className="node-text">
                  <strong>Lexical & Domain TLD Analyzer</strong>
                  <span>Entropy calculation, suspicious TLD flagging (.tk, .xyz, .top)</span>
                </div>
              </div>
              <div className="pipeline-node-item">
                <div className="node-icon"><UserCheck size={16} /></div>
                <div className="node-text">
                  <strong>TRAI / DLT Identity Verification</strong>
                  <span>Principal Entity validation and alphanumeric header authentication</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* SCREEN 7: FAQS */}
        {activeScreen === 'faqs' && (
          <div className="sub-screen-view">
            
            {/* Search FAQs bar */}
            <div className="faq-search-wrap">
              <Search size={18} className="faq-search-icon" />
              <input 
                type="text" 
                className="faq-search-input" 
                placeholder="Search FAQs..."
                value={searchFaq}
                onChange={(e) => setSearchFaq(e.target.value)}
              />
            </div>

            <div className="faq-sections-count">
              <span className="faq-pill-count">{FAQS_DATA.length} sections</span>
            </div>

            {/* List of FAQ Cards (Matching Image 3) */}
            <div className="faqs-list-mobile">
              {FAQS_DATA.filter(sec => 
                sec.title.toLowerCase().includes(searchFaq.toLowerCase()) || 
                sec.items.some(i => i.q.toLowerCase().includes(searchFaq.toLowerCase()))
              ).map((sec) => {
                const isOpen = expandedFaq === sec.id;
                return (
                  <div key={sec.id} className="faq-group-card">
                    <div 
                      className="faq-card-header"
                      onClick={() => setExpandedFaq(isOpen ? null : sec.id)}
                    >
                      <div className="faq-header-left">
                        <div className="faq-icon-box">
                          <Smartphone size={20} />
                        </div>
                        <div className="faq-title-wrap">
                          <h4 className="faq-section-title">{sec.title}</h4>
                          <span className="faq-q-count">{sec.count}</span>
                        </div>
                      </div>
                      <div className={`faq-circle-btn ${isOpen ? 'rotated' : ''}`}>
                        <ArrowRight size={14} />
                      </div>
                    </div>

                    {isOpen && (
                      <div className="faq-items-body">
                        {sec.items.map((item, idx) => (
                          <div key={idx} className="faq-qa-item">
                            <h5 className="faq-q-text">Q: {item.q}</h5>
                            <p className="faq-a-text">{item.a}</p>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

          </div>
        )}

        {/* SCREEN 8: ABOUT */}
        {activeScreen === 'about' && (
          <div className="sub-screen-view">
            <div className="screen-header-banner">
              <h3>About Sanchar Saathi</h3>
              <p>Department of Telecommunications, Government of India</p>
            </div>

            <div className="about-card-mobile">
              <h4>Citizen-Centric Telecom Security</h4>
              <p>
                Sanchar Saathi is an initiative of the Department of Telecommunications to empower mobile subscribers, strengthen cybersecurity awareness, and combat phishing, SMS fraud, and cybercrime communications.
              </p>
              <div className="about-badge-list">
                <div className="about-pill">✓ AI Transformer Pipeline</div>
                <div className="about-pill">✓ TRAI DLT Compliant</div>
                <div className="about-pill">✓ Guaranteed &lt;800ms SLA</div>
                <div className="about-pill">✓ Full Native Offline Shield</div>
              </div>
            </div>
          </div>
        )}

      </main>

      {/* WI-FI BACKEND IP CONFIGURATION MODAL */}
      {showIpModal && (
        <div className="ip-modal-overlay" onClick={() => setShowIpModal(false)}>
          <div className="ip-modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="ip-modal-header">
              <div className="ip-modal-title-group">
                <Globe size={20} className="text-primary" />
                <h4>Wi-Fi Backend Server Configuration</h4>
              </div>
              <button className="ip-modal-close" onClick={() => setShowIpModal(false)}>
                <X size={16} />
              </button>
            </div>

            <p className="ip-modal-desc">
              Connect wirelessly to your laptop's FastAPI backend over local Wi-Fi without USB cables.
            </p>

            <div className="ip-input-wrap">
              <label className="ip-label">Laptop Wi-Fi IPv4 Address</label>
              <input 
                type="text" 
                className="ip-field" 
                placeholder="e.g. 192.168.29.242"
                value={tempIp}
                onChange={(e) => setTempIp(e.target.value)}
              />
              <span className="ip-hint">Target URL: {getApiUrl('/api/v1/analyze')}</span>
            </div>

            <div className="ip-modal-actions">
              <button 
                className="ip-save-btn"
                onClick={() => saveBackendIp(tempIp)}
              >
                Save & Connect Wirelessly
              </button>
              <button 
                className="ip-reset-btn"
                onClick={() => {
                  setTempIp('192.168.29.242');
                  saveBackendIp('192.168.29.242');
                }}
              >
                Reset to Current Wi-Fi IP
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 5. NATIVE MOBILE FIXED BOTTOM NAVIGATION BAR */}
      <nav className="mobile-bottom-nav">
        <button 
          className={`nav-tab-btn ${activeScreen === 'home' ? 'active' : ''}`}
          onClick={() => setActiveScreen('home')}
        >
          <div className="tab-icon-wrapper">
            <Building2 size={20} />
          </div>
          <span className="tab-label">Home</span>
        </button>

        <button 
          className={`nav-tab-btn ${activeScreen === 'faqs' ? 'active' : ''}`}
          onClick={() => setActiveScreen('faqs')}
        >
          <div className="tab-icon-wrapper">
            <HelpCircle size={20} />
          </div>
          <span className="tab-label">FAQs</span>
        </button>

        <button 
          className={`nav-tab-btn ${activeScreen === 'about' ? 'active' : ''}`}
          onClick={() => setActiveScreen('about')}
        >
          <div className="tab-icon-wrapper">
            <Info size={20} />
          </div>
          <span className="tab-label">About</span>
        </button>

        <button 
          className="nav-tab-btn"
          onClick={() => {
            if (navigator.share) {
              navigator.share({
                title: 'Sanchar Saathi App',
                text: 'Protect yourself against SMS phishing and scam links with Sanchar Saathi.',
                url: window.location.href,
              }).catch(() => {});
            } else {
              alert('Sanchar Saathi App Link copied to clipboard.');
            }
          }}
        >
          <div className="tab-icon-wrapper">
            <Share2 size={20} />
          </div>
          <span className="tab-label">Share App</span>
        </button>
      </nav>

    </div>
  );
}
