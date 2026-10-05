package com.qbits.resources;

import com.qbits.resources.domain.ItemResource;
import com.qbits.resources.domain.ResourceLink;
import com.qbits.resources.persistence.ItemResourceRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Verified links for stories: ordered code, model, dataset, paper; at most 3 in total (R5.1). */
@Service
public class ResourceQueries {

  private final ItemResourceRepository resources;
  private final ResourcesProperties props;

  public ResourceQueries(ItemResourceRepository resources, ResourcesProperties props) {
    this.resources = resources;
    this.props = props;
  }

  public Map<UUID, List<ResourceLink>> verifiedFor(Collection<UUID> itemIds) {
    Map<UUID, List<ResourceLink>> out = new HashMap<>();
    resources
        .findVerified(itemIds)
        .forEach(
            (itemId, list) ->
                out.put(
                    itemId,
                    list.stream()
                        .sorted(
                            Comparator.comparing(ItemResource::type)
                                .thenComparing(ItemResource::id))
                        .map(ItemResource::asCandidate)
                        .distinct()
                        .limit(props.maxPerStory())
                        .map(
                            c ->
                                new ResourceLink(
                                    c.type().name().toLowerCase(Locale.ROOT),
                                    c.label(),
                                    c.url(),
                                    c.name()))
                        .toList()));
    return out;
  }
}
