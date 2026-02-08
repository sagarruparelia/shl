import LoadingSpinner from '../shared/LoadingSpinner';
import ErrorAlert from '../shared/ErrorAlert';

type Status = 'loading' | 'decrypting' | 'error';

interface Props {
  status: Status;
  message?: string;
}

export default function ViewerStatus({ status, message }: Props) {
  if (status === 'loading') return <LoadingSpinner message="Fetching health data..." />;
  if (status === 'decrypting') return <LoadingSpinner message="Decrypting content..." />;
  if (status === 'error') return <ErrorAlert message={message ?? 'An error occurred'} />;
  return null;
}
