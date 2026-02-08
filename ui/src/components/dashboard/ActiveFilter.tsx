type FilterValue = 'all' | 'active' | 'inactive';

interface Props {
  value: FilterValue;
  onChange: (value: FilterValue) => void;
}

const options: { value: FilterValue; label: string }[] = [
  { value: 'all', label: 'All' },
  { value: 'active', label: 'Active' },
  { value: 'inactive', label: 'Inactive' },
];

export default function ActiveFilter({ value, onChange }: Props) {
  return (
    <div className="inline-flex rounded-md border divide-x">
      {options.map((opt) => (
        <button
          key={opt.value}
          onClick={() => onChange(opt.value)}
          className={`px-3 py-1.5 text-sm first:rounded-l-md last:rounded-r-md transition-colors ${
            value === opt.value
              ? 'bg-indigo-600 text-white border-indigo-600'
              : 'hover:bg-gray-50'
          }`}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
}

export type { FilterValue };
