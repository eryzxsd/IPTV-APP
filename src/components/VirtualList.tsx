import { forwardRef, useEffect, useImperativeHandle, useRef, useState, type ReactNode } from 'react';

export interface VirtualListHandle {
  scrollToIndex(i: number): void;
}

interface Props {
  count: number;
  rowHeight: number;
  overscan?: number;
  className?: string;
  render: (index: number) => ReactNode;
}

/** Fixed-row-height virtual list — needed for playlists with 10k+ channels. */
export const VirtualList = forwardRef<VirtualListHandle, Props>(function VirtualList(
  { count, rowHeight, overscan = 8, className, render },
  ref,
) {
  const el = useRef<HTMLDivElement>(null);
  const [scroll, setScroll] = useState(0);
  const [height, setHeight] = useState(600);

  useEffect(() => {
    const node = el.current;
    if (!node) return;
    const ro = new ResizeObserver(() => setHeight(node.clientHeight));
    ro.observe(node);
    setHeight(node.clientHeight);
    return () => ro.disconnect();
  }, []);

  useImperativeHandle(ref, () => ({
    scrollToIndex(i: number) {
      const node = el.current;
      if (!node) return;
      const top = i * rowHeight;
      if (top < node.scrollTop) node.scrollTop = top;
      else if (top + rowHeight > node.scrollTop + node.clientHeight) node.scrollTop = top + rowHeight - node.clientHeight;
    },
  }));

  const first = Math.max(0, Math.floor(scroll / rowHeight) - overscan);
  const last = Math.min(count, Math.ceil((scroll + height) / rowHeight) + overscan);
  const rows: ReactNode[] = [];
  for (let i = first; i < last; i++) {
    rows.push(
      <div key={i} className="vrow" style={{ transform: `translateY(${i * rowHeight}px)`, height: rowHeight }}>
        {render(i)}
      </div>,
    );
  }

  return (
    <div ref={el} className={`vlist ${className || ''}`} onScroll={(e) => setScroll(e.currentTarget.scrollTop)}>
      <div style={{ height: count * rowHeight, position: 'relative' }}>{rows}</div>
    </div>
  );
});
