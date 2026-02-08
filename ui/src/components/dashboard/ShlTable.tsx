import type { ShlSummaryResponse } from '../../types/shl';
import ShlTableRow from './ShlTableRow';
import EmptyState from '../shared/EmptyState';

interface Props {
  items: ShlSummaryResponse[];
}

export default function ShlTable({ items }: Props) {
  if (items.length === 0) {
    return <EmptyState message="No SHL links found." />;
  }

  return (
    <div className="overflow-x-auto border rounded-lg">
      <table className="w-full">
        <thead>
          <tr className="bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
            <th className="px-4 py-3">Label</th>
            <th className="px-4 py-3">Flags</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">Contents</th>
            <th className="px-4 py-3">Accesses</th>
            <th className="px-4 py-3">Created</th>
          </tr>
        </thead>
        <tbody>
          {items.map((shl) => (
            <ShlTableRow key={shl.id} shl={shl} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
