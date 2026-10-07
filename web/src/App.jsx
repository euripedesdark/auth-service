import './i18n';
import { useTranslation } from 'react-i18next';
import { Dropdown } from 'primereact/dropdown';
import Admin from './Admin';
import User from './User';

const LANGS = [
  { code: 'pt-BR', name: 'Português (BR)' },
  { code: 'en-US', name: 'English (US)' },
  { code: 'es-ES', name: 'Español (ES)' },
  { code: 'fr-FR', name: 'Français (FR)' }
];

export default function App() {
  const { i18n } = useTranslation();
  const isUser = /(^|\/)mfa(\/|$)/.test(window.location.pathname);
  return (
    <div>
      <div className="flex justify-content-end p-2">
        <Dropdown value={i18n.language} options={LANGS} optionLabel="name" optionValue="code"
          onChange={e => i18n.changeLanguage(e.value)} placeholder="Idioma" />
      </div>
      {isUser ? <User /> : <Admin />}
    </div>
  );
}
