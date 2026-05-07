import { HttpInterceptorFn } from '@angular/common/http';

/**
 * This interceptor attaches JWT token to outgoing API calls.
 * It keeps authorization header handling out of components.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = localStorage.getItem('skybooker_token');

  if (!token) {
    return next(request);
  }

  const clonedRequest = request.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`
    }
  });

  return next(clonedRequest);
};
