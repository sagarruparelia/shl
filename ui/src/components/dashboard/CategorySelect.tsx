import { FHIR_CATEGORY_LIST, type FhirCategory } from '../../types/enums';

interface Props {
  selected: FhirCategory[];
  onChange: (selected: FhirCategory[]) => void;
}

export default function CategorySelect({ selected, onChange }: Props) {
  const toggle = (cat: FhirCategory) => {
    if (selected.includes(cat)) {
      onChange(selected.filter((c) => c !== cat));
    } else {
      onChange([...selected, cat]);
    }
  };

  return (
    <div>
      <label className="block text-sm font-medium mb-2">Categories</label>
      <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
        {FHIR_CATEGORY_LIST.map((cat) => (
          <label key={cat.value} className="flex items-center gap-2 text-sm cursor-pointer">
            <input
              type="checkbox"
              checked={selected.includes(cat.value as FhirCategory)}
              onChange={() => toggle(cat.value as FhirCategory)}
              className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
            />
            {cat.label}
          </label>
        ))}
      </div>
    </div>
  );
}
