import React, { useState, useEffect } from 'react';
import './App.css';
import { ApplyForm } from './ApplyForm';
import { MyRequests } from './MyRequests';
import { ManagerInbox } from './ManagerInbox';
import { getPendingLeaves } from './api';

type ActiveTab = 'apply' | 'my-requests' | 'manager-inbox';

const DEMO_USERS = [
  { name: 'Alice Smith', email: 'alice@example.com' },
  { name: 'Bob Jones', email: 'bob@example.com' },
  { name: 'Charlie Brown', email: 'charlie@example.com' },
];

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<ActiveTab>('apply');
  const [currentEmail, setCurrentEmail] = useState<string>('alice@example.com');
  const [pendingCount, setPendingCount] = useState<number>(0);
  const [refreshKey, setRefreshKey] = useState<number>(0);

  // Poll pending count
  useEffect(() => {
    const updateCount = () => {
      getPendingLeaves()
        .then(list => setPendingCount(list.length))
        .catch(() => {});
    };
    updateCount();
    const interval = setInterval(updateCount, 10000);
    return () => clearInterval(interval);
  }, [refreshKey]);

  const handleActionCompleted = () => {
    setRefreshKey(prev => prev + 1);
  };

  return (
    <div className="app-container">
      {/* Top Header */}
      <header className="app-header">
        <div className="brand-section">
          <div className="brand-icon">✈️</div>
          <div>
            <h1 className="brand-title">Leave Application System</h1>
            <p className="brand-subtitle">Camunda 8 BPMN Workflow &bull; Spring Boot 3 &bull; React Vite</p>
          </div>
        </div>

        {/* Current User Persona Selector */}
        <div className="user-badge-selector">
          <label htmlFor="user-select">Active User:</label>
          <select
            id="user-select"
            value={currentEmail}
            onChange={e => setCurrentEmail(e.target.value)}
          >
            {DEMO_USERS.map(u => (
              <option key={u.email} value={u.email}>
                {u.name} ({u.email})
              </option>
            ))}
          </select>
        </div>
      </header>

      {/* Navigation Tabs */}
      <nav className="nav-tabs" aria-label="Workflow Navigation">
        <button
          id="tab-apply"
          className={`nav-tab-btn ${activeTab === 'apply' ? 'active' : ''}`}
          onClick={() => setActiveTab('apply')}
        >
          <span>📝</span>
          <span>Apply for Leave</span>
        </button>

        <button
          id="tab-my-requests"
          className={`nav-tab-btn ${activeTab === 'my-requests' ? 'active' : ''}`}
          onClick={() => setActiveTab('my-requests')}
        >
          <span>📋</span>
          <span>My Requests</span>
        </button>

        <button
          id="tab-manager-inbox"
          className={`nav-tab-btn ${activeTab === 'manager-inbox' ? 'active' : ''}`}
          onClick={() => setActiveTab('manager-inbox')}
        >
          <span>📥</span>
          <span>Manager Inbox</span>
          {pendingCount > 0 && (
            <span className="tab-counter" id="inbox-pending-counter">{pendingCount}</span>
          )}
        </button>
      </nav>

      {/* Main Tab Content */}
      <main>
        {activeTab === 'apply' && (
          <ApplyForm
            currentEmail={currentEmail}
            onSuccess={() => {
              handleActionCompleted();
              setActiveTab('my-requests');
            }}
          />
        )}

        {activeTab === 'my-requests' && (
          <MyRequests key={`${currentEmail}-${refreshKey}`} currentEmail={currentEmail} />
        )}

        {activeTab === 'manager-inbox' && (
          <ManagerInbox onDecisionCompleted={handleActionCompleted} />
        )}
      </main>
    </div>
  );
};

export default App;
