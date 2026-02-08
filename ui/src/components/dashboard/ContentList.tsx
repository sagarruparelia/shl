import type { ContentSummary } from '../../types/shl';
import { formatDate, formatFileSize } from '../../lib/format';

interface Props {
  contents: ContentSummary[];
}

export default function ContentList({ contents }: Props) {
  if (contents.length === 0) {
    return <p className="text-sm text-gray-500">No content files.</p>;
  }

  return (
    <div className="overflow-x-auto border rounded-lg">
      <table className="w-full">
        <thead>
          <tr className="bg-gray-50 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">
            <th className="px-4 py-3">Filename</th>
            <th className="px-4 py-3">Content Type</th>
            <th className="px-4 py-3">Size</th>
            <th className="px-4 py-3">Created</th>
          </tr>
        </thead>
        <tbody>
          {contents.map((c) => (
            <tr key={c.id} className="border-t">
              <td className="px-4 py-3 text-sm">{c.originalFileName ?? c.id.slice(0, 8)}</td>
              <td className="px-4 py-3 text-sm text-gray-600">{c.contentType}</td>
              <td className="px-4 py-3 text-sm text-gray-600">{formatFileSize(c.contentLength)}</td>
              <td className="px-4 py-3 text-sm text-gray-500">{formatDate(c.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
