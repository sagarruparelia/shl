import { Link } from 'react-router';
import type { ShlSummaryResponse } from '../../types/shl';
import StatusBadge from '../shared/StatusBadge';
import FlagBadges from '../shared/FlagBadges';
import { formatDate } from '../../lib/format';

interface Props {
  shl: ShlSummaryResponse;
}

export default function ShlTableRow({ shl }: Props) {
  return (
    <tr className="border-t hover:bg-gray-50">
      <td className="px-4 py-3 text-sm">
        <Link to={`/dashboard/${shl.id}`} className="text-indigo-600 hover:underline font-medium">
          {shl.label || shl.id.slice(0, 8)}
        </Link>
      </td>
      <td className="px-4 py-3 text-sm">
        <FlagBadges flags={shl.flags} />
      </td>
      <td className="px-4 py-3 text-sm">
        <StatusBadge active={shl.active} expiresAt={shl.expiresAt} />
      </td>
      <td className="px-4 py-3 text-sm text-gray-600">{shl.contentCount}</td>
      <td className="px-4 py-3 text-sm text-gray-600">{shl.accessCount}</td>
      <td className="px-4 py-3 text-sm text-gray-500">{formatDate(shl.createdAt)}</td>
    </tr>
  );
}
