import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card } from 'primereact/card';
import { InputText } from 'primereact/inputtext';
import { InputOtp } from 'primereact/inputotp';
import { Button } from 'primereact/button';
import { Message } from 'primereact/message';
import { api, downloadUrl } from './api';

export default function User() {
  const { t } = useTranslation();
  const [user, setUser] = useState('');
  const [step, setStep] = useState(1);
  const [code, setCode] = useState('');
  const [msg, setMsg] = useState(null);

  function ir() {
    if (!user.trim()) return;
    setStep(2);
    setMsg(null);
  }

  async function verificar() {
    setMsg({ s: 'info', t: t('user.checking') });
    const v = await api('/api/v1/mfa/verify', { method: 'POST', body: { username: user.trim(), code: String(code).trim() } });
    if (!v.ok) { setMsg({ s: 'error', t: t('user.codeInvalid') }); return; }
    const r = await api('/api/v1/certificates/download-token', { method: 'POST', body: { username: user.trim(), mfaId: v.data.mfaId } });
    if (!r.ok) { setMsg({ s: 'warn', t: t('user.noPending') }); return; }
    setMsg({ s: 'success', t: t('user.downloading') });
    window.location = downloadUrl(r.data.downloadToken);
  }

  return (
    <div className="p-3" style={{ maxWidth: 520, margin: '0 auto' }}>
      <h1>{t('app.userTitle')}</h1>
      {step === 1 && (
        <Card title={t('user.step1')}>
          <div className="flex flex-column gap-2">
            <InputText value={user} onChange={e => setUser(e.target.value)} placeholder={t('user.userPh')} />
            <div><Button label={t('user.next')} icon="pi pi-arrow-right" onClick={ir} /></div>
          </div>
        </Card>
      )}
      {step === 2 && (
        <Card title={t('user.step2')}>
          <div className="flex flex-column gap-2">
            <p>{t('user.help')}</p>
            <InputOtp value={code} onChange={e => setCode(e.value)} length={6} integerOnly />
            <div><Button label={t('user.verify')} icon="pi pi-check" onClick={verificar} /></div>
            {msg && <Message severity={msg.s} text={msg.t} />}
          </div>
        </Card>
      )}
    </div>
  );
}
