export type LeaveType = 'ANNUAL' | 'SICK' | 'UNPAID';

export type LeaveStatus = 'SUBMITTED' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export interface LeaveRequest {
  id: number;
  employeeName: string;
  employeeEmail: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  workingDays: number;
  reason: string;
  status: LeaveStatus;
  statusMessage?: string;
  processInstanceKey?: number;
  taskKey?: number;
  managerComment?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SubmitLeaveRequest {
  employeeName: string;
  employeeEmail: string;
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  reason: string;
}

export interface DecisionRequest {
  approved: boolean;
  comment?: string;
}

export interface LeaveBalance {
  id: number;
  employeeEmail: string;
  annualEntitlement: number;
  remainingDays: number;
}

export interface ApiError {
  status: number;
  error: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

const BASE_URL = '/api/leaves';

async function handleResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return {} as T;
  }

  const contentType = response.headers.get('content-type');
  const isJson = contentType && contentType.includes('application/json');
  const data = isJson ? await response.json() : await response.text();

  if (!response.ok) {
    const errorObj: ApiError = {
      status: response.status,
      error: response.statusText || 'Error',
      message: typeof data === 'object' && data.message ? data.message : String(data || 'Request failed'),
      fieldErrors: typeof data === 'object' && data.fieldErrors ? data.fieldErrors : undefined,
    };
    throw errorObj;
  }

  return data as T;
}

/**
 * Submits a new leave request.
 * POST /api/leaves
 */
export async function submitLeave(payload: SubmitLeaveRequest): Promise<LeaveRequest> {
  const res = await fetch(BASE_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return handleResponse<LeaveRequest>(res);
}

/**
 * Retrieves all leave requests for a given employee.
 * GET /api/leaves?employeeEmail=...
 */
export async function getMyLeaves(employeeEmail: string): Promise<LeaveRequest[]> {
  const url = `${BASE_URL}?employeeEmail=${encodeURIComponent(employeeEmail)}`;
  const res = await fetch(url);
  return handleResponse<LeaveRequest[]>(res);
}

/**
 * Retrieves all leave requests currently awaiting manager approval.
 * GET /api/leaves/pending
 */
export async function getPendingLeaves(): Promise<LeaveRequest[]> {
  const res = await fetch(`${BASE_URL}/pending`);
  return handleResponse<LeaveRequest[]>(res);
}

/**
 * Submits a manager approval or rejection decision.
 * POST /api/leaves/{id}/decision
 */
export async function submitDecision(id: number, payload: DecisionRequest): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}/decision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
  return handleResponse<void>(res);
}

/**
 * Retrieves employee leave balance.
 * GET /api/leaves/balance?employeeEmail=...
 */
export async function getLeaveBalance(employeeEmail: string): Promise<LeaveBalance> {
  const url = `${BASE_URL}/balance?employeeEmail=${encodeURIComponent(employeeEmail)}`;
  const res = await fetch(url);
  return handleResponse<LeaveBalance>(res);
}

/**
 * Client-side calculation of working days (excluding Saturday & Sunday).
 */
export function calculateWorkingDaysClient(start: string, end: string): number {
  if (!start || !end) return 0;
  const startDate = new Date(start + 'T00:00:00');
  const endDate = new Date(end + 'T00:00:00');

  if (isNaN(startDate.getTime()) || isNaN(endDate.getTime())) return 0;
  if (endDate < startDate) return 0;

  let count = 0;
  const cur = new Date(startDate);
  while (cur <= endDate) {
    const day = cur.getDay(); // 0 is Sunday, 6 is Saturday
    if (day !== 0 && day !== 6) {
      count++;
    }
    cur.setDate(cur.getDate() + 1);
  }
  return count;
}
