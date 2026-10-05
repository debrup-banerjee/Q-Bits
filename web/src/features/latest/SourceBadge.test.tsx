import { render, screen } from '@testing-library/react';
import { badgeColour, SourceBadge } from './SourceBadge';

// 004 R4.1, R4.4
it('shows the first letter, upper-cased, and no image', () => {
  const { container } = render(<SourceBadge name="mint" />);
  expect(screen.getByTestId('source-badge')).toHaveTextContent('M');
  expect(container.querySelector('img')).toBeNull();
});

// 004 R4.1
it('gives a source the same colour every time', () => {
  expect(badgeColour('The Hindu')).toBe(badgeColour('The Hindu'));
  const colours = new Set(
    ['The Hindu', 'TechCrunch', 'MIT News', 'BBC News', 'Mint', 'WIRED'].map(badgeColour),
  );
  expect(colours.size).toBeGreaterThan(1);
});
