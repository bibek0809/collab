import { userColor, initials } from '../../utils/helpers';

export default function Avatar({ userId, name, size = 'md', ring = false, className = '' }) {
  const sz = { sm: 'w-6 h-6 text-[9px]', md: 'w-8 h-8 text-[11px]', lg: 'w-10 h-10 text-sm' }[size];
  const ringCls = ring ? 'ring-2 ring-white' : '';

  return (
    <div
      className={`${sz} rounded-full flex items-center justify-center
                  font-bold text-white shrink-0 ${ringCls} ${className}`}
      style={{ backgroundColor: userColor(userId) }}
      title={name || userId}
    >
      {initials(name || userId || '?')}
    </div>
  );
}
