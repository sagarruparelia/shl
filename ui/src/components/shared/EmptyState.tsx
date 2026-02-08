interface Props {
  message: string;
  action?: React.ReactNode;
}

export default function EmptyState({ message, action }: Props) {
  return (
    <div className="text-center py-12">
      <p className="text-gray-500">{message}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
