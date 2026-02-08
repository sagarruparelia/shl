import { flagLabel } from '../../lib/format';

const FLAG_COLORS: Record<string, string> = {
  L: 'bg-blue-100 text-blue-700',
  P: 'bg-amber-100 text-amber-700',
  U: 'bg-purple-100 text-purple-700',
};

interface Props {
  flags: string;
}

export default function FlagBadges({ flags }: Props) {
  if (!flags) return null;

  return (
    <span className="inline-flex gap-1">
      {[...flags].map((f) => (
        <span
          key={f}
          className={`inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium ${FLAG_COLORS[f] ?? 'bg-gray-100 text-gray-700'}`}
        >
          {flagLabel(f)}
        </span>
      ))}
    </span>
  );
}
