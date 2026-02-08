import { NavLink } from 'react-router';

const links = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/dashboard/create', label: 'Create SHL' },
];

export default function Nav() {
  return (
    <nav className="flex gap-4">
      {links.map((l) => (
        <NavLink
          key={l.to}
          to={l.to}
          end
          className={({ isActive }) =>
            `text-sm font-medium px-3 py-1.5 rounded-md transition-colors ${
              isActive
                ? 'bg-white text-indigo-700 shadow-sm'
                : 'text-indigo-100 hover:text-white hover:bg-indigo-500'
            }`
          }
        >
          {l.label}
        </NavLink>
      ))}
    </nav>
  );
}
