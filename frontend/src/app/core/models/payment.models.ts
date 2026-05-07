export type PaymentMode = 'UPI' | 'CARD' | 'NET_BANKING' | 'WALLET';

export interface InitiatePaymentRequest {
  bookingId: string;
  userId: number;
  amount: number;
  currency: string;
  paymentMode: PaymentMode;
}

export interface ProcessPaymentRequest {
  paymentId: string;
  success: boolean;
  transactionId?: string;
  gatewayResponse?: string;
}

export interface VerifyPaymentRequest {
  bookingId: string;
  paymentId?: string;
  razorpayOrderId?: string;
  razorpayPaymentId?: string;
  razorpaySignature?: string;
  gatewayResponse?: string;
}

export interface RefundPaymentRequest {
  paymentId: string;
  amount: number;
}

export interface PaymentResponse {
  paymentId: string;
  bookingId: string;
  userId: number;
  amount: number;
  currency: string;
  status: 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
  paymentMode: PaymentMode;
  razorpayKeyId?: string;
  gatewayOrderId?: string;
  transactionId?: string;
  gatewayResponse?: string;
  createdAt?: string;
  paidAt?: string;
  refundedAt?: string;
  refundedAmount?: number;
}

export interface PaymentKeyResponse {
  key: string;
}
