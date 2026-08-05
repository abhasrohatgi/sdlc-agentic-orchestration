package com.agenticsdlc.shortener.adapter.persistence;

import com.agenticsdlc.shortener.port.LinkRepository;
import com.agenticsdlc.shortener.port.LinkRepositoryContract;
import org.junit.jupiter.api.DisplayName;

/** Runs the {@link LinkRepositoryContract} against the in-memory adapter. */
@DisplayName("InMemoryLinkRepository honours the LinkRepository contract")
class InMemoryLinkRepositoryTest extends LinkRepositoryContract {

    @Override
    protected LinkRepository repository() {
        return new InMemoryLinkRepository();
    }
}
