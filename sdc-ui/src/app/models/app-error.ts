import { HttpErrorResponse } from '@angular/common/http';

export type AppError =
  | { kind: 'client'; message: string }
  | { kind: 'server'; message: string }
  | { kind: 'network'; baseUrl: string };

export function parseHttpError(err: HttpErrorResponse, baseUrl: string): AppError {
  if (err.status === 0) return { kind: 'network', baseUrl };
  if (err.status < 500) return { kind: 'client', message: err.error?.message ?? err.message };
  return { kind: 'server', message: err.error?.message ?? 'Internal server error' };
}
