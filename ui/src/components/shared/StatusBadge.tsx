interface Props {
  active: boolean;
  expiresAt?: string | null;
}

export default function StatusBadge({ active, expiresAt }: Props) {
  const expired = expiresAt ? new Date(expiresAt).getTime() < Date.now() : false;

  let label: string;
  let className: string;

  if (!active) {
    label = 'Inactive';
    className = 'bg-gray-100 text-gray-700';
  } else if (expired) {
    label = 'Expired';
    className = 'bg-red-100 text-red-700';
  } else {
    label = 'Active';
    className = 'bg-green-100 text-green-700';
  }

  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
