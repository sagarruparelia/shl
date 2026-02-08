import { Routes, Route, Navigate } from 'react-router';
import AppLayout from './components/layout/AppLayout';
import DashboardPage from './pages/DashboardPage';
import CreateShlPage from './pages/CreateShlPage';
import ShlDetailPage from './pages/ShlDetailPage';
import ViewerPage from './pages/ViewerPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route element={<AppLayout />}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/dashboard/create" element={<CreateShlPage />} />
        <Route path="/dashboard/:id" element={<ShlDetailPage />} />
      </Route>
      <Route path="/view" element={<ViewerPage />} />
      <Route path="/view.html" element={<ViewerPage />} />
    </Routes>
  );
}
