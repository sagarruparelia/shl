interface Props {
  content: string;
  contentType: string;
  index: number;
}

export default function DecryptedContent({ content, contentType, index }: Props) {
  let formatted: string;
  try {
    formatted = JSON.stringify(JSON.parse(content), null, 2);
  } catch {
    formatted = content;
  }

  return (
    <div className="border rounded-lg overflow-hidden">
      <div className="bg-gray-50 px-4 py-2 border-b flex items-center justify-between">
        <span className="text-xs font-medium text-gray-500">
          File {index + 1} — {contentType}
        </span>
      </div>
      <pre className="p-4 text-xs font-mono overflow-x-auto max-h-[600px] overflow-y-auto whitespace-pre-wrap">
        {formatted}
      </pre>
    </div>
  );
}
