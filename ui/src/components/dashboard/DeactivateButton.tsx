import { useState } from 'react';
import { useNavigate } from 'react-router';
import { deactivateShl } from '../../api/shl';
import ConfirmDialog from '../shared/ConfirmDialog';

interface Props {
  shlId: string;
  active: boolean;
}

export default function DeactivateButton({ shlId, active }: Props) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  if (!active) return null;

  const handleConfirm = async () => {
    setLoading(true);
    try {
      await deactivateShl(shlId);
      navigate('/dashboard');
    } catch {
      setLoading(false);
      setOpen(false);
    }
  };

  return (
    <>
      <button
        onClick={() => setOpen(true)}
        disabled={loading}
        className="px-4 py-2 text-sm font-medium text-red-600 border border-red-300 rounded-md hover:bg-red-50"
      >
        {loading ? 'Deactivating...' : 'Deactivate'}
      </button>
      <ConfirmDialog
        open={open}
        title="Deactivate SHL"
        message="This will permanently deactivate the link. Recipients will no longer be able to access the shared data."
        confirmLabel="Deactivate"
        onConfirm={handleConfirm}
        onCancel={() => setOpen(false)}
      />
    </>
  );
}
