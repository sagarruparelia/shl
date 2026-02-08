interface Props {
  page: number;
  size: number;
  totalElements: number;
  onNext: () => void;
  onPrev: () => void;
}

export default function Pagination({ page, size, totalElements, onNext, onPrev }: Props) {
  const totalPages = Math.ceil(totalElements / size);
  if (totalPages <= 1) return null;

  return (
    <div className="flex items-center justify-between pt-4">
      <p className="text-sm text-gray-600">
        Page {page + 1} of {totalPages} ({totalElements} total)
      </p>
      <div className="flex gap-2">
        <button
          onClick={onPrev}
          disabled={page === 0}
          className="px-3 py-1.5 text-sm border rounded-md disabled:opacity-40 hover:bg-gray-50"
        >
          Previous
        </button>
        <button
          onClick={onNext}
          disabled={page >= totalPages - 1}
          className="px-3 py-1.5 text-sm border rounded-md disabled:opacity-40 hover:bg-gray-50"
        >
          Next
        </button>
      </div>
    </div>
  );
}
