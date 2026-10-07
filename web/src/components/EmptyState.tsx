export function EmptyState({ sectionName }: { sectionName: string }) {
  return (
    <p className="rounded-2xl border border-dashed border-line bg-surface/60 p-8 text-center text-muted">
      No {sectionName} news in the last 72 hours. Check back soon.
    </p>
  );
}
